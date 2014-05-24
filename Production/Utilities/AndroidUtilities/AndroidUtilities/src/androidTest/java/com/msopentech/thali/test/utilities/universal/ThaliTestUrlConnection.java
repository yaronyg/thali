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
import com.msopentech.thali.CouchDBListener.*;
import com.msopentech.thali.test.utilities.*;
import com.msopentech.thali.testinfrastructure.*;
import com.msopentech.thali.utilities.universal.*;
import org.ektorp.*;

import javax.net.ssl.*;
import java.io.*;
import java.security.*;

public class ThaliTestUrlConnection extends ThaliTestCase {

    public void testThaliUrlConnection()
            throws InterruptedException, UnrecoverableEntryException, KeyManagementException, NoSuchAlgorithmException,
            KeyStoreException, IOException {
        String host = ThaliListener.DefaultThaliDeviceHubAddress;
        char[] passPhrase = ThaliCryptoUtilities.DefaultPassPhrase;
        CreateClientBuilder createClientBuilder = this.getCreateClientBuilder();
        Context context = this.getCouchbaseContext();

        ThaliTestUtilities.configuringLoggingApacheClient();

        ThaliListener thaliTestServer = new ThaliListener();
        File keyStore = ThaliCryptoUtilities.getThaliKeyStoreFileObject(context.getFilesDir());

        // We want to start with a clean state
        if (keyStore.exists()) {
            keyStore.delete();
        }

        // We use a random port (e.g. port 0) both because it's good hygiene and because it keeps us from conflicting
        // with the 'real' Thali Device Hub if it's running.
        thaliTestServer.startServer(context, 0);

        int port = thaliTestServer.getSocketStatus().getPort();

        CouchDbInstance couchDbInstance =
                ThaliClientToDeviceHubUtilities.GetLocalCouchDbInstance(
                        context.getFilesDir(), createClientBuilder, host, port, passPhrase);

        couchDbInstance.deleteDatabase(ThaliTestUtilities.TestDatabaseName);
        couchDbInstance.createDatabase(ThaliTestUtilities.TestDatabaseName);

        KeyStore clientKeyStore = ThaliCryptoUtilities.validateThaliKeyStore(context.getFilesDir());

        org.apache.http.client.HttpClient httpClientNoServerValidation =
                createClientBuilder.CreateApacheClient(host, port, null, clientKeyStore, passPhrase);

        PublicKey serverPublicKey =
                ThaliClientToDeviceHubUtilities.getServersRootPublicKey(
                        httpClientNoServerValidation);

        String httpsURL = "https://" + host + ":" + port + "/" + ThaliTestUtilities.TestDatabaseName + "/";

        HttpsURLConnection httpsURLConnection =
                ThaliUrlConnection.getThaliUrlConnection(httpsURL, serverPublicKey, clientKeyStore, passPhrase);

        httpsURLConnection.setRequestMethod("GET");
        int responseCode = httpsURLConnection.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException();
        }
    }
}
