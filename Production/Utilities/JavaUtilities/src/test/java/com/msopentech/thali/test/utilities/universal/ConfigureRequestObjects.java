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
import com.msopentech.thali.test.utilities.*;
import com.msopentech.thali.test.utilities.universal.*;
import com.msopentech.thali.utilities.universal.*;
import org.apache.commons.io.*;
import org.ektorp.*;

import java.io.*;
import java.security.*;

/**
 * Running a non-trivial test, one where clients talk to servers and such requires a whole mess of objects.
 * This class generates them.
 */
public class ConfigureRequestObjects {
    public static final String clientSubDirectory = "client";
    public final ThaliCouchDbInstance thaliCouchDbInstance;
    public final CouchDbConnector testDatabaseConnector;
    public final CouchDbConnector replicationDatabaseConnector;
    public final PublicKey clientPublicKey;
    public final PublicKey serverPublicKey;
    public final KeyStore clientKeyStore;

    public ConfigureRequestObjects(String host, int port, char[] passPhrase,
                                   CreateClientBuilder createClientBuilder, Context context)
            throws NoSuchAlgorithmException, IOException, UnrecoverableEntryException, KeyStoreException,
            KeyManagementException  {

        File clientFilesDir = new File(context.getFilesDir(), clientSubDirectory);

        // We want to start with a new identity
        if (clientFilesDir.exists()) {
            FileUtils.deleteDirectory(clientFilesDir);
        }

        if (clientFilesDir.mkdirs() == false) {
            throw new RuntimeException();
        }

        thaliCouchDbInstance =
                ThaliClientToDeviceHubUtilities.GetLocalCouchDbInstance(
                        clientFilesDir, createClientBuilder, host, port, passPhrase);

        thaliCouchDbInstance.deleteDatabase(ThaliTestUtilities.TestDatabaseName);
        thaliCouchDbInstance.deleteDatabase(ThaliTestEktorpClient.ReplicationTestDatabaseName);

        testDatabaseConnector = thaliCouchDbInstance.createConnector(ThaliTestUtilities.TestDatabaseName, false);

        clientKeyStore = ThaliCryptoUtilities.validateThaliKeyStore(clientFilesDir);

        org.apache.http.client.HttpClient httpClientNoServerValidation =
                createClientBuilder.CreateApacheClient(host, port, null, clientKeyStore, passPhrase);

        serverPublicKey =
                ThaliClientToDeviceHubUtilities.getServersRootPublicKey(
                        httpClientNoServerValidation);

        KeyStore.PrivateKeyEntry clientPrivateKeyEntry =
                (KeyStore.PrivateKeyEntry)
                        clientKeyStore.getEntry(
                                ThaliCryptoUtilities.ThaliKeyAlias, new KeyStore.PasswordProtection(passPhrase));

        clientPublicKey = clientPrivateKeyEntry.getCertificate().getPublicKey();

        replicationDatabaseConnector = thaliCouchDbInstance.createConnector(
                ThaliTestEktorpClient.ReplicationTestDatabaseName, false);

        // Last but not least we need to provision the databases own key inside itself. The reason is
        // that we are doing replication tests where the database talks to itself. So if its own key isn't
        // in its own authorization database then it won't let itself talk to itself!
        ThaliClientToDeviceHubUtilities.configureKeyInServersKeyDatabase(serverPublicKey, thaliCouchDbInstance);
    }
}
