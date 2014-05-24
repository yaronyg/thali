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

package com.msopentech.thali.utilities.java.test;

import com.msopentech.thali.utilities.java.JavaFXBridgeManager;
import com.msopentech.thali.utilities.webviewbridge.BridgeManager;
import com.msopentech.thali.utilities.webviewbridge.BridgeManagerTest;
import javafx.application.Application;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.assertTrue;

public class JavaFXBridgeManagerTest {
    protected static BridgeManagerTest bridgeManagerTest = null;

    public static class AppHosting extends Application {
        @Override
        public void start(Stage stage) throws FileNotFoundException {
            WebView browser = new WebView();
            WebEngine webEngine = browser.getEngine();
            BridgeManager bridgeManager = new JavaFXBridgeManager(webEngine);
            bridgeManagerTest.launchTest(bridgeManager);
        }
    }

    @Test
    public void testBridgeManager() throws InterruptedException {
        bridgeManagerTest = new BridgeManagerTest();
        new Thread() {
            public void run() {
                Application.launch(AppHosting.class, new String[0]);
            }
        }.start();
        assertTrue(bridgeManagerTest.testResult());
    }
}
