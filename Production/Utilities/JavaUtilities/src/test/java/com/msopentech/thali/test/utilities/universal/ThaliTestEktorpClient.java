/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

THIS CODE IS PROVIDED ON AN *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED,
INCLUDING WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A PARTICULAR PURPOSE,
MERCHANTABLITY OR NON-INFRINGEMENT.

See the Apache 2 License for the specific language governing permissions and limitations under the License.
*/

package com.msopentech.thali.test.utilities.universal;

import com.couchbase.lite.*;
import com.couchbase.lite.replicator.*;
import com.couchbase.lite.util.*;
import com.msopentech.thali.CouchDBListener.*;
import com.msopentech.thali.test.utilities.*;
import com.msopentech.thali.testinfrastructure.*;
import com.msopentech.thali.utilities.universal.*;
import org.ektorp.*;
import org.ektorp.http.*;
import org.ektorp.impl.*;
import org.ektorp.support.*;

import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * This class contains all the generic test code to exercise both the Ektorp client as well as CouchBase Lite.
 * Because of inheritance issues I can't make this into a true test class and instead have to rely on the code
 * in the AndroidUtilities and JavaUtilities projects to properly bind this class. I have put in some simple
 * checks to try and detect when the child hasn't bound all the various methods.
 *
 * This test assumes that there is one instance of the thali listener that is used across all the different
 * tests. This is necessary to work around a bug in TJWS (https://github.com/couchbase/couchbase-lite-java-listener/issues/43)
 * that keeps threads (and file locks) when you think you have killed TJWS.
 */
public class ThaliTestEktorpClient extends ThaliTestCase {
    public static final String KeyId = "key";
    public static final String ReplicationTestDatabaseName = "replicationtest";

    public static final int MaximumTestRecords = 10;

    private String host;
    private char[] passPhrase;
    private Context context;
    private CreateClientBuilder createClientBuilder;
    private int port;

    private ThaliListener thaliTestServer = null;
    private ConfigureRequestObjects configureRequestObjects;

    public void setUp() throws InterruptedException, UnrecoverableEntryException, NoSuchAlgorithmException,
            KeyStoreException, KeyManagementException, IOException {
        ThaliTestUtilities.configuringLoggingApacheClient();

        this.host = ThaliListener.DefaultThaliDeviceHubAddress;
        this.passPhrase = ThaliCryptoUtilities.DefaultPassPhrase;
        this.context = this.getCouchbaseContext();
        this.createClientBuilder = this.getCreateClientBuilder();

        thaliTestServer = new ThaliListener();


        // We use a random port (e.g. port 0) both because it's good hygiene and because it keeps us from conflicting
        // with the 'real' Thali Device Hub if it's running.
        thaliTestServer.startServer(context, 0);

        port = thaliTestServer.getSocketStatus().getPort();

        configureRequestObjects = new ConfigureRequestObjects(host, port, passPhrase, createClientBuilder, context);
    }

    @Override
    public void tearDown() {
        thaliTestServer.stopServer();
    }

    public void testPullReplication() throws InvalidKeySpecException, NoSuchAlgorithmException,
            InterruptedException, MalformedURLException, CouchbaseLiteException, URISyntaxException {
        replicationTestEngine(false);
    }

    public void testPushReplication() throws UnrecoverableEntryException, KeyManagementException, NoSuchAlgorithmException,
            KeyStoreException, IOException, InterruptedException, InvalidKeySpecException, URISyntaxException,
            CouchbaseLiteException {
        replicationTestEngine(true);
    }

    /**
     * This code tests replicating data between two databases, one is local and the other is remote. The push
     * variable controls if replication is push based (in which case the local database will initially be filled
     * with data and the remote database will be empty) or pull base (in which case the local database will be left
     * empty initially and the remote database filled with data).
     * @param push
     * @throws java.security.spec.InvalidKeySpecException
     * @throws java.security.NoSuchAlgorithmException
     * @throws InterruptedException
     * @throws java.net.MalformedURLException
     * @throws com.couchbase.lite.CouchbaseLiteException
     * @throws java.net.URISyntaxException
     */
    protected void replicationTestEngine(boolean push) throws InvalidKeySpecException, NoSuchAlgorithmException,
            InterruptedException, MalformedURLException, CouchbaseLiteException, URISyntaxException {
        String localName = ThaliTestUtilities.TestDatabaseName;
        String remoteName = ThaliTestEktorpClient.ReplicationTestDatabaseName;
        final CouchDbConnector connectorForInitiallyFullDb =
                push ? configureRequestObjects.testDatabaseConnector : configureRequestObjects.replicationDatabaseConnector;
        final CouchDbConnector connectorForInitiallyEmptyDb =
                push ? configureRequestObjects.replicationDatabaseConnector : configureRequestObjects.testDatabaseConnector;

        // The setup should handle this but I'm being paranoid
        configureRequestObjects.thaliCouchDbInstance.deleteDatabase(localName);
        configureRequestObjects.thaliCouchDbInstance.deleteDatabase(remoteName);

        // Set up docs and then replicate from full to empty, in push, the remote is empty, in pull the local is empty
        ThaliTestUtilities.setUpData(configureRequestObjects.thaliCouchDbInstance, push ? localName : remoteName,
                1, ThaliTestEktorpClient.MaximumTestRecords, configureRequestObjects.clientPublicKey);
        ReplicateAndTest(localName, remoteName, push, false);

        // Add and remove and alter a doc from the full database and re-replicate
        ThaliTestUtilities.GenerateDoc(connectorForInitiallyFullDb);
        ThaliTestUtilities.DeleteDoc(connectorForInitiallyFullDb);
        ThaliTestUtilities.AlterDoc(connectorForInitiallyFullDb);
        ReplicateAndTest(localName, remoteName, push, false);

        // Reverse the direction of the replication and see if anything changes
        ReplicateAndTest(remoteName, localName, push, false);

        // Add and remove and alter a doc from the 'empty' and reverse the replication and see if it works
        ThaliTestUtilities.AlterDoc(connectorForInitiallyEmptyDb);
        ThaliTestUtilities.DeleteDoc(connectorForInitiallyEmptyDb);
        ThaliTestUtilities.GenerateDoc(connectorForInitiallyEmptyDb);
        ReplicateAndTest(remoteName, localName, push, false);

        // Add and remove and alter a doc from initially full DB and then set up a continuous replication
        ThaliTestUtilities.GenerateDoc(connectorForInitiallyFullDb);
        ThaliTestUtilities.AlterDoc(connectorForInitiallyFullDb);
        ThaliTestUtilities.DeleteDoc(connectorForInitiallyFullDb);
        ReplicationChangeListener initiallyFullToInitiallyEmptyChangeListener =
                ReplicateAndTest(localName, remoteName, push, true);

        // Add and remove docs and see if the continuous replication picks it up
        Execute execute = new Execute() {
            @Override
            public void runit() {
                ThaliTestUtilities.DeleteDoc(connectorForInitiallyFullDb);
                ThaliTestUtilities.GenerateDoc(connectorForInitiallyFullDb);
                ThaliTestUtilities.AlterDoc(connectorForInitiallyFullDb);
            }
        };
        ValidateExistingContinuousReplication(
                connectorForInitiallyFullDb, connectorForInitiallyEmptyDb, initiallyFullToInitiallyEmptyChangeListener,
                execute);

        // Set up a continuous replication in the opposite direction (but leave the other replication running)
        ReplicationChangeListener initiallyEmptyToInitiallyFullChangeListener =
                ReplicateAndTest(remoteName, localName, push, true);

        // Add and remove docs from 'target' and see if the changes safely make it to 'source'
        execute = new Execute() {
            @Override
            public void runit() {
                ThaliTestUtilities.DeleteDoc(connectorForInitiallyEmptyDb);
                ThaliTestUtilities.GenerateDoc(connectorForInitiallyEmptyDb);
                ThaliTestUtilities.GenerateDoc(connectorForInitiallyEmptyDb);
                ThaliTestUtilities.AlterDoc(connectorForInitiallyEmptyDb);
                ThaliTestUtilities.GenerateDoc(connectorForInitiallyEmptyDb);
            }
        };

        ValidateExistingContinuousReplication(
                connectorForInitiallyEmptyDb, connectorForInitiallyFullDb, initiallyEmptyToInitiallyFullChangeListener,
                execute);
    }

    /**
     * Runs a test where we set a user key in one database and then post to another.
     * @throws java.io.IOException
     * @throws java.security.KeyManagementException
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.security.UnrecoverableKeyException
     * @throws java.security.KeyStoreException
     */
    public void testRetrieve()
            throws IOException, KeyManagementException, NoSuchAlgorithmException, UnrecoverableEntryException,
            KeyStoreException, InvalidKeySpecException, InterruptedException {
        ConfigureRequestObjects configureRequestObjects =
                new ConfigureRequestObjects(host, port, passPhrase, createClientBuilder, context);

        Collection<CouchDbDocument> testDocuments = ThaliTestUtilities.setUpData(
                configureRequestObjects.thaliCouchDbInstance, ThaliTestUtilities.TestDatabaseName, 1,
                MaximumTestRecords, configureRequestObjects.clientPublicKey);
        ThaliTestUtilities.validateDatabaseState(configureRequestObjects.testDatabaseConnector, testDocuments);
        runBadKeyTest(host, port, createClientBuilder, configureRequestObjects.serverPublicKey,
                configureRequestObjects.clientKeyStore, passPhrase);
    }

    protected void checkIfChildClassExecutesAllTests(Class childClass) {
        if (childClass == this.getClass()) {
            throw new RuntimeException("No cheating! You have to put the real child class!");
        }

        HashSet<String> thisTestMethods = getListOfMethodsThatStartWith(this.getClass(), "test");
        HashSet<String> childTestMethods = getListOfMethodsThatStartWith(childClass, "test");

        if (thisTestMethods.size() != childTestMethods.size()) {
            throw new RuntimeException("The number of test methods in this class and in its parent class don't match! Something was missed or added!");
        }

        for(String thisMethodName : thisTestMethods) {
            if (childTestMethods.contains(thisMethodName) == false) {
                throw new RuntimeException("The test method " + thisMethodName + " was not implemented in the child test class");
            }
        }

        // This only verifies the exitence of setUp and tearDown but it doesn't actually prove that they call
        // the matching methods here.
        HashSet<String> setUpMethods = getListOfMethodsThatStartWith(childClass, "setUp");
        HashSet<String> tearDownmethods = getListOfMethodsThatStartWith(childClass, "tearDown");

        if (setUpMethods.contains("setUp") == false || tearDownmethods.contains("tearDown") == false) {
            throw new RuntimeException("Either setUp or tearDown is missing!");
        }

    }

    private HashSet<String> getListOfMethodsThatStartWith(Class classToExamine, String startsWith) {
        HashSet<String> testMethods = new HashSet<String>();
        for( Method method : classToExamine.getDeclaredMethods()) {
            if (method.getName().startsWith(startsWith)) {
                // TODO: Put in a check for the number of arguments being used, for all our current cases it's 0.
                testMethods.add(method.getName());
            }
        }
        return testMethods;
    }

    /**
     * Lets us listen in on the replication changes, we use this to know when the replicator has entered
     * certain states.
     */
    protected class ReplicationChangeListener implements Replication.ChangeListener {
        public final Semaphore callWhenSynchDone;
        public final Replication.ReplicationStatus replicationStatus;

        public ReplicationChangeListener(Replication.ReplicationStatus replicationStatus) throws InterruptedException {
            callWhenSynchDone = new Semaphore(1);
            callWhenSynchDone.acquire();
            this.replicationStatus = replicationStatus;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            Log.d("ick", event.getSource().getStatus().toString());
            if (event.getSource().getStatus() == replicationStatus) {
                callWhenSynchDone.release();
            }
        }
    }

    protected interface Execute {
        public void runit();
    }

    protected static void ValidateExistingContinuousReplication(CouchDbConnector sourceConnector,
                                                                CouchDbConnector targetConnector,
                                                                ReplicationChangeListener replicationChangeListener,
                                                                Execute execute)
            throws InterruptedException {

        if (replicationChangeListener != null) {
            // Due to https://github.com/couchbase/couchbase-lite-android-core/issues/55 we can't be sure that the semaphore
            // was cleared so we might have to clear it manually.
            if (replicationChangeListener.callWhenSynchDone.availablePermits() == 0) {
                replicationChangeListener.callWhenSynchDone.release();
            }
            replicationChangeListener.callWhenSynchDone.acquire();
        }
        execute.runit();
        ValidateReplicationCompletion(true, replicationChangeListener, sourceConnector, targetConnector);
    }

    /**
     * Either pushes from source to target or pulls from target to source depending on the value of push.
     * @param source
     * @param target
     * @param push
     * @param continuous
     * @throws InterruptedException
     * @throws java.net.MalformedURLException
     * @throws java.net.URISyntaxException
     * @return
     */
    protected ReplicationChangeListener ReplicateAndTest(String source, String target, boolean push, boolean continuous)
            throws InterruptedException, MalformedURLException, URISyntaxException, CouchbaseLiteException {
        HttpKeyURL targetUrl =
                new HttpKeyURL(configureRequestObjects.serverPublicKey, host, port,
                        target, null, null);
        ThaliReplicationCommand thaliReplicationCommand =
                new ThaliReplicationCommand.Builder()
                        .source(push ? source : targetUrl.toString())
                        .target(push ? targetUrl.toString() : source)
                        .createTarget(true)
                        .continuous(continuous)
                        .build();
        ReplicationStatus replicationStatus =
                configureRequestObjects.thaliCouchDbInstance.replicate(thaliReplicationCommand);
        if (replicationStatus.isOk() == false) {
            throw new RuntimeException("Replication failed!");
        }

        ReplicationChangeListener replicationChangeListener = null;

        if (thaliTestServer != null) {
            Manager manager = thaliTestServer.getManager();
            Database database = manager.getDatabase(source);
            URL targetHttpsUrl = new URL(targetUrl.createHttpsUrl());
            Replication replication = database.getActiveReplicator(targetHttpsUrl, push);
            replicationChangeListener =
                    new ReplicationChangeListener(
                            continuous ? Replication.ReplicationStatus.REPLICATION_IDLE :
                                    Replication.ReplicationStatus.REPLICATION_STOPPED);
            replication.addChangeListener(replicationChangeListener);
        }

        CouchDbConnector sourceTestConnector = configureRequestObjects.thaliCouchDbInstance.createConnector(source, false);
        CouchDbConnector targetTestConnector = configureRequestObjects.thaliCouchDbInstance.createConnector(target, false);
        return ValidateReplicationCompletion(continuous, replicationChangeListener, sourceTestConnector, targetTestConnector);
    }

    private static ReplicationChangeListener ValidateReplicationCompletion(
            boolean continuous, ReplicationChangeListener replicationChangeListener,
            CouchDbConnector sourceTestConnector, CouchDbConnector targetTestConnector) throws InterruptedException {
        // The notifications in CouchBase, especially for pull requests, are a bit half hazard. This is
        // an occupational hazard with pull requests since the long poll mechanism used makes it a guess
        // as to what things like 'idle' even mean. So for continuous requests we just try a couple of times
        // to see if the replication has finished.
        int maxRepeatCount = continuous ? 3 : 1;
        Exception lastException = null;
        boolean doWeThinkWeAreDone = replicationChangeListener == null;
        for(int repeatCount = 0; repeatCount < maxRepeatCount; ++repeatCount) {
            if (doWeThinkWeAreDone) {
                Thread.sleep(10*1000);
            }

            // TODO: doWeThinkWeAreDone is an overly complex optimization that in some theoretical cases might
            // make us run faster. We should probably remove it.
            doWeThinkWeAreDone =
                    replicationChangeListener == null ||
                            replicationChangeListener.callWhenSynchDone.tryAcquire(10, TimeUnit.SECONDS);

            try {
                ThaliTestUtilities.validateDatabaseEquality(sourceTestConnector, targetTestConnector);
                return replicationChangeListener;
            } catch (Exception e) {
                lastException = e;
            }
        }

        throw new RuntimeException("Compare failed.", lastException);
    }


    /**
     * Try to connect to a DB with a client key we know is not authorized
     * @param host
     * @param port
     * @param createClientBuilder
     * @param actualServerPublicKey
     * @param actualClientKeyStore
     * @param clientPassPhrase
     * @throws java.security.UnrecoverableKeyException
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.security.KeyStoreException
     * @throws java.security.KeyManagementException
     */
    protected static void runBadKeyTest(String host, int port, CreateClientBuilder createClientBuilder,
                                     PublicKey actualServerPublicKey, KeyStore actualClientKeyStore,
                                     char[] clientPassPhrase) throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        KeyPair wrongKeys = ThaliCryptoUtilities.GenerateThaliAcceptablePublicPrivateKeyPair();

        HttpClient httpClientWithWrongServerKeyAndRightClientKey =
                createClientBuilder.CreateEktorpClient(host, port, wrongKeys.getPublic(), actualClientKeyStore, clientPassPhrase);

        CouchDbInstance couchDbInstance = new StdCouchDbInstance(httpClientWithWrongServerKeyAndRightClientKey);

        try {
            CouchDbConnector couchDbConnector = couchDbInstance.createConnector(ThaliTestUtilities.TestDatabaseName, true);
            throw new RuntimeException();
        } catch (Exception e) {
            ThaliTestUtilities.assertFail(e.getCause() instanceof SSLException);
        }

        KeyStore wrongClientKeyStore =
                ThaliCryptoUtilities.CreatePKCS12KeyStoreWithPublicPrivateKeyPair(wrongKeys, "foo",
                        ThaliCryptoUtilities.DefaultPassPhrase);

        HttpClient httpClientWithRightServerKeyAndWrongClientKey =
                createClientBuilder.CreateEktorpClient(host, port, actualServerPublicKey, wrongClientKeyStore,
                        ThaliCryptoUtilities.DefaultPassPhrase);

        couchDbInstance = new StdCouchDbInstance(httpClientWithRightServerKeyAndWrongClientKey);
        try {
            CouchDbConnector couchDbConnector = couchDbInstance.createConnector(ThaliTestUtilities.TestDatabaseName, true);
            throw new RuntimeException();
        } catch (DbAccessException e) {
        }
    }
}
