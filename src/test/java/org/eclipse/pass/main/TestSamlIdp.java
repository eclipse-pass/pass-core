/*
 * Copyright 2026 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.pass.main;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AttributeValue;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.Signer;
import org.springframework.security.saml2.core.OpenSamlInitializationService;
import org.w3c.dom.Element;

/**
 * A minimal SAML identity provider which runs inside the test JVM.
 * <p>
 * It creates signed SAML responses which can be posted directly to the assertion consumer
 * service of pass-core. This is an IDP initiated login which Spring Security accepts.
 * The response is processed by pass-core exactly as one from a real IDP would be.
 * <p>
 * The key used for signing and the metadata which makes pass-core trust it are in
 * src/test/resources/saml2/. The test configuration in application-test.yml uses that metadata.
 */
public final class TestSamlIdp {
    /**
     * Must match the entityID in saml2/idp-metadata.xml.
     */
    public static final String ENTITY_ID = "https://idp.pass.test/idp";

    /**
     * Must match the SingleSignOnService location in saml2/idp-metadata.xml.
     */
    public static final String SSO_URL = "https://idp.pass.test/sso";

    // Names of the attributes expected by PassAuthenticationFilter. They mimic those of Azure AD.
    public static final String DISPLAY_NAME = "urn:oid:2.16.840.1.113730.3.1.241";
    public static final String SCOPED_AFFILIATION = "urn:oid:1.3.6.1.4.1.5923.1.1.1.9";
    public static final String EMAIL = "urn:oid:0.9.2342.19200300.100.1.3";
    public static final String EPPN = "urn:oid:1.3.6.1.4.1.5923.1.1.1.6";
    public static final String GIVEN_NAME = "urn:oid:2.5.4.42";
    public static final String SURNAME = "urn:oid:2.5.4.4";
    public static final String EMPLOYEE_ID = "urn:oid:2.16.840.1.113730.3.1.3";
    public static final String UNIQUE_ID = "urn:oid:1.3.6.1.4.1.5923.1.1.1.13";

    /**
     * Attributes of a test user with a submitter role.
     */
    public static final Map<String, List<String>> USER1 = Map.of(
        DISPLAY_NAME, List.of("Sally M. Submitter"),
        SCOPED_AFFILIATION, List.of("MEMBER@johnshopkins.edu", "FACULTY@johnshopkins.edu"),
        EMAIL, List.of("sally123456789@jhu.edu"),
        EPPN, List.of("sallysubmitter123456789@johnshopkins.edu"),
        GIVEN_NAME, List.of("Sally"),
        SURNAME, List.of("Submitter"),
        EMPLOYEE_ID, List.of("123456789"),
        UNIQUE_ID, List.of("sms123456789@johnshopkins.edu"));

    /**
     * Attributes of a second test user with a submitter role.
     */
    public static final Map<String, List<String>> USER2 = Map.of(
        DISPLAY_NAME, List.of("Thomas L. Submitter"),
        SCOPED_AFFILIATION, List.of("FACULTY@johnshopkins.edu"),
        EMAIL, List.of("tom987654321@jhu.edu"),
        EPPN, List.of("thomassubmitter987654321@johnshopkins.edu"),
        GIVEN_NAME, List.of("Tom"),
        SURNAME, List.of("Submitter"),
        EMPLOYEE_ID, List.of("987654321"),
        UNIQUE_ID, List.of("tls987654321@johnshopkins.edu"));

    private static final String KEY_RESOURCE = "/saml2/idp-key.pem";
    private static final String CERT_RESOURCE = "/saml2/idp-cert.pem";
    private static final String ATTRIBUTE_NAME_FORMAT = "urn:oasis:names:tc:SAML:2.0:attrname-format:uri";
    private static final Duration VALIDITY = Duration.ofMinutes(5);

    private static final BasicX509Credential CREDENTIAL;

    static {
        OpenSamlInitializationService.initialize();

        try {
            CREDENTIAL = new BasicX509Credential(load_certificate(), load_private_key());
        } catch (IOException | GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TestSamlIdp() {
    }

    private static byte[] load_resource(String name) throws IOException {
        try (InputStream is = TestSamlIdp.class.getResourceAsStream(name)) {
            if (is == null) {
                throw new IOException("Missing resource: " + name);
            }

            return is.readAllBytes();
        }
    }

    private static X509Certificate load_certificate() throws IOException, GeneralSecurityException {
        try (InputStream is = TestSamlIdp.class.getResourceAsStream(CERT_RESOURCE)) {
            if (is == null) {
                throw new IOException("Missing resource: " + CERT_RESOURCE);
            }

            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(is);
        }
    }

    private static PrivateKey load_private_key() throws IOException, GeneralSecurityException {
        String pem = new String(load_resource(KEY_RESOURCE), StandardCharsets.US_ASCII);
        String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");

        return KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends XMLObject> T build(QName name) {
        return (T) XMLObjectSupport.buildXMLObject(name);
    }

    private static String new_id() {
        return "_" + UUID.randomUUID();
    }

    private static Issuer issuer() {
        Issuer issuer = build(Issuer.DEFAULT_ELEMENT_NAME);
        issuer.setValue(ENTITY_ID);
        return issuer;
    }

    /**
     * Create a signed SAML response asserting a user with the given attributes.
     *
     * @param acs_url location of the assertion consumer service of the service provider
     * @param sp_id entity id of the service provider
     * @param attributes map of attribute name to values
     * @return base64 encoded response suitable for use as the SAMLResponse form parameter
     */
    public static String createResponse(String acs_url, String sp_id, Map<String, List<String>> attributes) {
        return createResponse(acs_url, sp_id, attributes, true);
    }

    /**
     * Create a SAML response asserting a user with the given attributes.
     *
     * @param acs_url location of the assertion consumer service of the service provider
     * @param sp_id entity id of the service provider
     * @param attributes map of attribute name to values
     * @param sign whether or not to sign the response
     * @return base64 encoded response suitable for use as the SAMLResponse form parameter
     */
    public static String createResponse(String acs_url, String sp_id, Map<String, List<String>> attributes,
            boolean sign) {
        Instant now = Instant.now();
        Instant expires = now.plus(VALIDITY);

        Response response = build(Response.DEFAULT_ELEMENT_NAME);
        response.setID(new_id());
        response.setVersion(SAMLVersion.VERSION_20);
        response.setIssueInstant(now);
        response.setDestination(acs_url);
        response.setIssuer(issuer());

        StatusCode status_code = build(StatusCode.DEFAULT_ELEMENT_NAME);
        status_code.setValue(StatusCode.SUCCESS);
        Status status = build(Status.DEFAULT_ELEMENT_NAME);
        status.setStatusCode(status_code);
        response.setStatus(status);

        Assertion assertion = build(Assertion.DEFAULT_ELEMENT_NAME);
        assertion.setID(new_id());
        assertion.setVersion(SAMLVersion.VERSION_20);
        assertion.setIssueInstant(now);
        assertion.setIssuer(issuer());

        NameID name_id = build(NameID.DEFAULT_ELEMENT_NAME);
        name_id.setFormat(NameIDType.TRANSIENT);
        name_id.setValue(new_id());

        SubjectConfirmationData confirmation_data = build(SubjectConfirmationData.DEFAULT_ELEMENT_NAME);
        confirmation_data.setRecipient(acs_url);
        confirmation_data.setNotOnOrAfter(expires);

        SubjectConfirmation confirmation = build(SubjectConfirmation.DEFAULT_ELEMENT_NAME);
        confirmation.setMethod(SubjectConfirmation.METHOD_BEARER);
        confirmation.setSubjectConfirmationData(confirmation_data);

        Subject subject = build(Subject.DEFAULT_ELEMENT_NAME);
        subject.setNameID(name_id);
        subject.getSubjectConfirmations().add(confirmation);
        assertion.setSubject(subject);

        Audience audience = build(Audience.DEFAULT_ELEMENT_NAME);
        audience.setURI(sp_id);
        AudienceRestriction restriction = build(AudienceRestriction.DEFAULT_ELEMENT_NAME);
        restriction.getAudiences().add(audience);

        Conditions conditions = build(Conditions.DEFAULT_ELEMENT_NAME);
        conditions.setNotBefore(now);
        conditions.setNotOnOrAfter(expires);
        conditions.getAudienceRestrictions().add(restriction);
        assertion.setConditions(conditions);

        AuthnContextClassRef class_ref = build(AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
        class_ref.setURI(AuthnContext.PASSWORD_AUTHN_CTX);
        AuthnContext authn_context = build(AuthnContext.DEFAULT_ELEMENT_NAME);
        authn_context.setAuthnContextClassRef(class_ref);

        AuthnStatement authn_statement = build(AuthnStatement.DEFAULT_ELEMENT_NAME);
        authn_statement.setAuthnInstant(now);
        authn_statement.setSessionIndex(new_id());
        authn_statement.setAuthnContext(authn_context);
        assertion.getAuthnStatements().add(authn_statement);

        AttributeStatement attribute_statement = build(AttributeStatement.DEFAULT_ELEMENT_NAME);

        attributes.forEach((name, values) -> {
            Attribute attribute = build(Attribute.DEFAULT_ELEMENT_NAME);
            attribute.setName(name);
            attribute.setNameFormat(ATTRIBUTE_NAME_FORMAT);

            for (String value : values) {
                // Must use the builder for the type in order to get a string value
                XSString xs_value = (XSString) XMLObjectProviderRegistrySupport.getBuilderFactory()
                        .getBuilder(XSString.TYPE_NAME)
                        .buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
                xs_value.setValue(value);
                attribute.getAttributeValues().add(xs_value);
            }

            attribute_statement.getAttributes().add(attribute);
        });

        if (!attributes.isEmpty()) {
            assertion.getAttributeStatements().add(attribute_statement);
        }

        response.getAssertions().add(assertion);

        Signature signature = null;

        if (sign) {
            signature = build(Signature.DEFAULT_ELEMENT_NAME);
            signature.setSigningCredential(CREDENTIAL);
            signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
            signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
            response.setSignature(signature);
        }

        try {
            Element element = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(response)
                    .marshall(response);

            if (signature != null) {
                Signer.signObject(signature);
            }

            return Base64.getEncoder().encodeToString(serialize(element).getBytes(StandardCharsets.UTF_8));
        } catch (MarshallingException | SignatureException | TransformerException e) {
            throw new IllegalStateException("Failed to create SAML response", e);
        }
    }

    private static String serialize(Element element) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(element), new StreamResult(writer));

        return writer.toString();
    }
}
