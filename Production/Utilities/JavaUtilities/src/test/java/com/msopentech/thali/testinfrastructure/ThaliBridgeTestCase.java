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

package com.msopentech.thali.testinfrastructure;

import com.couchbase.lite.*;
import com.msopentech.thali.test.*;
import com.msopentech.thali.utilities.java.*;
import com.msopentech.thali.utilities.universal.*;
import com.msopentech.thali.utilities.webviewbridge.*;
import javafx.application.*;
import javafx.scene.web.*;
import javafx.stage.*;
import junit.framework.*;

public class ThaliBridgeTestCase extends TestCase implements ThaliBridgeTestCaseBase {
    private static BridgeManager bridgeManager = null;
    protected static WebView browser;

    public static class AppHosting extends Application {
        @Override
        public void start(Stage primaryStage) throws Exception {
            browser = new WebView();
            WebEngine webEngine = browser.getEngine();
            bridgeManager = new JavaFXBridgeManager(webEngine);
        }
    }

    public ThaliBridgeTestCase() {
        new Thread() {
            public void run() {
                Application.launch(AppHosting.class, new String[0]);
            }
        }.start();

        // Poor man's synchronization
        while(bridgeManager == null) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public BridgeManager getBridgeManager() {
        return bridgeManager;
    }

    public void loadHtmlInWebView(final String url) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                browser.getEngine().load(url);
            }
        });
    }

    @Override
    public String getBaseURLForTestFiles() {
        return getClass().getResource("/thali/").toExternalForm();
    }

    @Override
    public CreateClientBuilder getCreateClientBuilder() {
        return new JavaEktorpCreateClientBuilder();
    }

    @Override
    public Context getContextWithNewSubdirectory() {
        return new ContextInTempDirectory();
    }
}
