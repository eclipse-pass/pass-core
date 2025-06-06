/*
 *
 * Copyright 2022 Johns Hopkins University
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.eclipse.pass.doi.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.yahoo.elide.RefreshableElide;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.PassClientResult;
import org.eclipse.pass.object.PassClientSelector;
import org.eclipse.pass.object.RSQL;
import org.eclipse.pass.object.model.Journal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages Journal objects related to a journal lookup on Crossref - creating or updating
 * a Journal through the Elide interface when necessary
 *
 * @author jrm
 */
public class ElideConnector {
    private static final Logger LOG = LoggerFactory.getLogger(ElideConnector.class);

    /**
     * The Elide instance to use for all Elide operations
     */
    protected RefreshableElide refreshableElide;

    /**
     * Constructor for ElideConnector
     *
     * @param refreshableElide the Elide instance to use for all Elide operations
     */
    protected ElideConnector(RefreshableElide refreshableElide) {
        this.refreshableElide = refreshableElide;
    }

    /**
     * This is the only method interfacing with the repo that the Servlet calls -
     * it orchestrates the process of building a Journal object from the supplied JSON object,
     * seeing if the Journal is present in PASS, creating or updating that Journal if needed,
     * and finally returning the PASS id for the Journal
     *
     * @param xrefJsonObject the supplied crossref JSON object
     * @return the id of the corresponding Journal object in PASS
     */
    protected String resolveJournal(JsonObject xrefJsonObject) {

        String journalId = null;

        try (PassClient passClient = PassClient.newInstance(refreshableElide)) {
            // we have something JSONy, let's build a journal object from it
            Journal journal = buildPassJournal(xrefJsonObject);

            // and compare it with what we already have in PASS, updating PASS if necessary
            Journal updatedJournal = updateJournalInPass(journal, passClient);

            //we return the journal id if we have one

            if (updatedJournal != null) {
                journalId = updatedJournal.getId().toString();
            }
        } catch (Exception e) {
            LOG.error("Error resolving journal", e);
        }
        return journalId;
    }

    /**
     * Takes JSON which represents journal article metadata from Crossref
     * and populates a new Journal object. Currently, we take typed issns and the journal
     * name.
     *
     * @param metadata - the JSON metadata from Crossref
     * @return the PASS journal object;s id
     */
    protected Journal buildPassJournal(JsonObject metadata) {
        final String XREF_MESSAGE = "message";
        final String XREF_TITLE = "container-title";
        final String XREF_ISSN_TYPE_ARRAY = "issn-type";
        final String XREF_ISSN_ARRAY = "ISSN";
        final String XREF_ISSN_TYPE = "type";
        final String XREF_ISSN_VALUE = "value";

        Journal passJournal = new Journal();

        JsonObject messageObject = metadata.getJsonObject(XREF_MESSAGE);
        JsonArray containerTitleArray = messageObject.getJsonArray(XREF_TITLE);
        JsonArray issnTypeArray = messageObject.getJsonArray(XREF_ISSN_TYPE_ARRAY);
        JsonArray issnArray = messageObject.getJsonArray(XREF_ISSN_ARRAY);

        if (!containerTitleArray.isNull(0)) {
            passJournal.setJournalName(containerTitleArray.getString(0));
        }

        Set<String> processedIssns = new HashSet<>();

        if (issnTypeArray != null) {
            for (int i = 0; i < issnTypeArray.size(); i++) {
                JsonObject issn = issnTypeArray.getJsonObject(i);

                String type = "";

                //translate crossref issn-type strings to PASS issn-type strings
                if (IssnType.PRINT.getCrossrefTypeString().equals(issn.getString(XREF_ISSN_TYPE))) {
                    type = IssnType.PRINT.getPassTypeString();
                } else if (IssnType.ELECTRONIC.getCrossrefTypeString()
                                              .equals(issn.getString(XREF_ISSN_TYPE))) {
                    type = IssnType.ELECTRONIC.getPassTypeString();
                }

                //collect the value for this issn
                String value = issn.getString(XREF_ISSN_VALUE);
                processedIssns.add(value);

                if (value.length() > 0) {
                    passJournal.getIssns().add(String.join(":", type, value));
                }
            }
        }

        if (issnArray != null) {
            for (int i = 0; i < issnArray.size(); i++) {
                // if we have issns which were not given as typed, we add them without a type
                String issn = issnArray.getString(i);
                if (!processedIssns.contains(issn)) {
                    passJournal.getIssns().add(":" + issn);//do this to conform with type:value format
                }
            }
        }

        passJournal.setId(null); // we don't need this
        return passJournal;
    }

    /**
     * Take a Journal object constructed from Crossref metadata, and compare it with the
     * version of this object which we have in PASS. Construct the most complete Journal
     * object possible from the two sources - PASS objects are more authoritative. Use the
     * Crossref version if we don't have it already in PASS. Store the resulting object in PASS.
     *
     * @param journal - the Journal object generated from Crossref metadata
     * @param passClient - the PASS client
     * @return the updated Journal object stored in PASS if the PASS object needs updating; null if we don't have
     * enough info to create a journal
     *
     * @throws IOException if the connection to Elide was unsuccessful
     */
    protected Journal updateJournalInPass(Journal journal, PassClient passClient) throws IOException {
        List<String> issns = journal.getIssns();
        String name = journal.getJournalName();

        //see if we have this in PASS
        Journal passJournal = find(name, issns, passClient);

        //create or update the pass version of this Journal
        if (passJournal == null) {
            // we don't have this journal in pass yet
            if (name != null && !name.isEmpty() && issns.size() > 0) {
                // but we have enough info to make a Journal entry
                passClient.createObject(journal);
                passJournal = new Journal(find(name, issns, passClient));
            } else {
                // do not have enough to create a new journal
                LOG.warn("Not enough info for journal " + name);
            }
        } else {
            updateJournalIfNeeded(passJournal, journal, passClient);
        }

        return passJournal;
    }

    private void updateJournalIfNeeded(Journal passJournal, Journal journal, PassClient passClient) throws IOException {
        if (isUpdatedByJournalLoader(passJournal)) {
            return;
        }
        boolean needsUpdate = false;
        if (!passJournal.getIssns().containsAll(journal.getIssns())) {
            List<String> newIssnList = Stream.concat(passJournal.getIssns().stream(),
                    journal.getIssns().stream()).distinct()
                .collect(Collectors.toList());
            passJournal.setIssns(newIssnList);
            needsUpdate = true;

        }
        if (!Objects.equals(passJournal.getJournalName(), journal.getJournalName())) {
            passJournal.setJournalName(journal.getJournalName());
            needsUpdate = true;
        }
        if (needsUpdate) {
            passClient.updateObject(passJournal);
        }
    }

    private boolean isUpdatedByJournalLoader(Journal journal) {
        return Objects.nonNull(journal.getNlmta()) || Objects.nonNull(journal.getPmcParticipation());
    }

    /**
     * Find a journal in our repository. We take the best match we can find. finder algorithm here should harmonize
     * with the approach in the {@code BatchJournalFinder} in the journal loader code
     *
     * @param name  the name of the journal to be found
     * @param issns the set of issns to find. we assume that the issns stored in the repo are of the format type:value
     * @param passClient - the PASS client
     * @return the URI of the best match, or null in nothing matches
     *
     * @throws IOException if the connection to Elide was unsuccessful
     *
     */
    protected Journal find(String name, List<String> issns, PassClient passClient) throws IOException {

        //keep track of hits for searches
        List<Journal> foundList = new ArrayList<>();

        //look for journals with this name
        String filter = RSQL.equals("journalName", name);
        PassClientResult<Journal> result = passClient.
            selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
        foundList.addAll(result.getObjects());

        //look for journals with any of these issns
        if (!issns.isEmpty()) {
            for (String issn : issns) {
                filter = RSQL.hasMember("issns", issn);
                result = passClient.
                    selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
                result.getObjects().forEach(j -> {
                    foundList.add(j);
                });
            }
        }

        //count the number of hits for each Journal
        if (foundList.size() == 0) {
            return null;
        } else {
            Map<Journal, Long> scoreMap = foundList.stream()
                                                   .collect(Collectors.groupingBy(e -> e, Collectors.counting()));

            // we have matches, pick the best one
            Long highScore = Collections.max(scoreMap.values());

            for (Journal journal : scoreMap.keySet()) {
                if (scoreMap.get(journal).equals(highScore)) {
                    return journal;
                }
            }
        }
        return null; //never reached
    }

    /**
     * a convenience enum for translating type strings for issns
     */
    public enum IssnType {
        /**
         * Pass Journal object type for print issns
         */
        PRINT,
        /**
         * Pass Journal object type for electronic issns
         */
        ELECTRONIC;

        static {
            // these values represent how types are stored on the issn field for the PASS Journal object
            PRINT.passTypeString = "Print";
            ELECTRONIC.passTypeString = "Online";
        }

        static {
            // these values represent how issn types are presented in Crossref metadata
            PRINT.crossrefTypeString = "print";
            ELECTRONIC.crossrefTypeString = "electronic";
        }

        private String passTypeString;
        private String crossrefTypeString;

        /**
         *
         * @return The value of how types are stored in the issn field for the Pass Journal object
         */
        public String getPassTypeString() {
            return passTypeString;
        }

        /**
         *
         * @return The value of how issn types are presented in Crossref metadata
         */
        public String getCrossrefTypeString() {
            return crossrefTypeString;
        }
    }
}