package org.eclipse.pass.object;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.yahoo.elide.RefreshableElide;
import org.eclipse.pass.main.IntegrationTest;
import org.eclipse.pass.object.model.Grant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

public class FullTextSearchTest extends IntegrationTest {
    @Autowired
    protected RefreshableElide refreshableElide;
    private PassClient dataStorePassClient;

    @BeforeAll
    public void setup() throws IOException {
        PassClient dataStorePassClient = new ElideDataStorePassClient(refreshableElide);

        Grant grant1 = new Grant();
        grant1.setAwardNumber("1234");
        Grant grant2 = new Grant();
        grant2.setAwardNumber("1234-1");

        dataStorePassClient.createObject(grant1);
        dataStorePassClient.createObject(grant2);

    }
    @Test
    public void awardSearchWithHyphen() throws IOException {
        String filter = RSQL.equals("awardNumber", "1234");
        PassClientResult<Grant> result = dataStorePassClient.selectObjects(new PassClientSelector<>(Grant.class, 0,
                100, filter, null));
        assertEquals(2, result.getObjects().size());
    }
}
