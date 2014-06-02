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

package com.msopentech.thali.test.utilities.webviewbridge;

import com.msopentech.thali.testinfrastructure.*;
import com.msopentech.thali.utilities.webviewbridge.*;

import java.net.*;

public class BridgeManagerTest extends ThaliBridgeTestCase {
    public static Object waitObject = new Object();
    public static String pathToBridgeHandlerTestHtml = "test/BridgeManagerTest/test.html";

    public enum pingStatus { unset, failed, success }
    public static pingStatus seenPing2 = pingStatus.unset;

    public static class BridgeTest extends BridgeHandler {
        public BridgeTest() {
            super("Test");
            seenPing2 = pingStatus.unset;
        }

        @Override
        public void call(final String jsonString, final BridgeCallBack bridgeCallBack) {
            // We throw the processing on a different thread to test that we are reasonably thread safe
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (jsonString.equals("\"Ping\"")) {
                        bridgeCallBack.successHandler("\"Pong\"");
                        return;
                    }

                    if (jsonString.equals("\"Ping1\"")) {
                        bridgeCallBack.failureHandler("\"Pong1\"");
                        return;
                    }

                    if (jsonString.equals("\"Ping2\"")) {
                        seenPing2 = pingStatus.success;
                        synchronized (waitObject) {
                            waitObject.notifyAll();
                        }
                        return;
                    }

                    throw new RuntimeException("Unrecognized call value: " + jsonString);
                }
            }).start();
        }
    }

    public void testBridge() throws MalformedURLException, InterruptedException {
        getBridgeManager().register(new BridgeTest());
        loadHtmlInWebView(new URL(new URL(getBaseURLForTestFiles()), pathToBridgeHandlerTestHtml).toExternalForm());

        synchronized (waitObject) {
            while(seenPing2 == pingStatus.unset) {
                waitObject.wait();
            }
        }

        assertEquals(seenPing2, pingStatus.success);
    }
}
