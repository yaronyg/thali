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

package com.msopentech.thali.test.utilities.xmlhttprequestbridge;

import com.couchbase.lite.*;
import com.couchbase.lite.util.*;
import com.msopentech.thali.CouchDBListener.*;
import com.msopentech.thali.testinfrastructure.*;
import com.msopentech.thali.utilities.webviewbridge.*;
import com.msopentech.thali.utilities.xmlhttprequestbridge.*;
import com.msopentech.thali.utilities.xmlhttprequestbridge.Bridge;

import java.net.*;

public class XhrTest extends ThaliBridgeTestCase {
    public static Object waitObject = new Object();
    public static String testHtml = "test/xhrtest/test.html";
    public enum pingStatus { unset, failed, success }
    public static pingStatus seenPing = pingStatus.unset;
    public ThaliListener thaliListenerFirstHub, thaliListenerSecondHub;

    public static class BridgeTest extends BridgeHandler {
        public BridgeTest() {
            super("Test");
            seenPing = pingStatus.unset;
        }

        @Override
        public void call(final String jsonString, final BridgeCallBack bridgeCallBack) {
            // We throw the processing on a different thread to test that we are reasonably thread safe
            new Thread(new Runnable() {
                @Override
                public void run() {
                    seenPing = jsonString.equals("\"Good\"") ? pingStatus.success : pingStatus.failed;
                    synchronized (waitObject) {
                        waitObject.notifyAll();
                    }
                }
            }).start();
        }
    }

    public static class LogHandler extends BridgeHandler {

        public LogHandler() {
            super("log");
        }

        @Override
        public void call(String jsonString, BridgeCallBack bridgeCallBack) {
            Log.e("src/resourcesToZip/xhrtest", jsonString);
        }
    }


    public XhrTest() {
        // Wacky useful for debugging what's on the wire!
        //ThaliTestUtilities.configuringLoggingApacheClient();
    }

    public void testXhrBridge() throws MalformedURLException, InterruptedException {
        bridgeManager = getBridgeManager();
        Context contextForFirstHub = getContextWithNewSubdirectory();
        Context contextForSecondHub = getContextWithNewSubdirectory();

        startServers(contextForFirstHub, contextForSecondHub);

        BridgeHandler xmlhttpBridge = new Bridge(contextForFirstHub.getFilesDir(), getCreateClientBuilder());
        bridgeManager.registerIfNameNotTaken(xmlhttpBridge);

        BridgeHandler bridgeTestHandler = new BridgeTest();
        bridgeManager.registerIfNameNotTaken(bridgeTestHandler);

        BridgeHandler logHandler = new BridgeTestManager.LogHandler();
        bridgeManager.registerIfNameNotTaken(logHandler);

        loadHtmlInWebView(new URL(new URL(getBaseURLForTestFiles()), testHtml).toExternalForm());

        synchronized(waitObject) {
            while(seenPing == pingStatus.unset) {
                waitObject.wait();
            }
        }
        thaliListenerFirstHub.stopServer();
        thaliListenerSecondHub.stopServer();
        assertEquals(seenPing, pingStatus.success);
    }

    protected void startServers(Context contextForFirstHub, Context contextForSecondHub) throws InterruptedException {
        thaliListenerFirstHub = new ThaliListener();
        thaliListenerFirstHub.startServer(contextForFirstHub, ThaliListener.DefaultThaliDeviceHubPort);

        thaliListenerSecondHub = new ThaliListener();
        thaliListenerSecondHub.startServer(contextForSecondHub, ThaliListener.DefaultThaliDeviceHubPort + 1);

        // This is a poor man's synch solution to make sure the test doesn't start before the listeners are running.
        thaliListenerFirstHub.getSocketStatus();
        thaliListenerSecondHub.getSocketStatus();
    }
}
