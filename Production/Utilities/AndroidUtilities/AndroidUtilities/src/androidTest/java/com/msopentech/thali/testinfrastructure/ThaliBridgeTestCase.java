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

import android.os.*;
import android.test.*;
import com.couchbase.lite.*;
import com.msopentech.thali.test.*;
import com.msopentech.thali.utilities.android.*;
import com.msopentech.thali.utilities.universal.*;
import com.msopentech.thali.utilities.webviewbridge.*;

/**
 * I have no been able to find a good mock activity. So I create a real activity but to test with a real activity
 * one has to use a different test clas than AndroidUnitTest used in ThaliTestCase.
 */
public class ThaliBridgeTestCase extends ActivityInstrumentationTestCase2<AndroidBridgeManagerTestActivity>
    implements ThaliBridgeTestCaseBase {
    private BridgeManager bridgeManager = null;

    public ThaliBridgeTestCase() {
        super(AndroidBridgeManagerTestActivity.class);

//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
//            throw new RuntimeException("This test only runs in KitKat or higher!");
//        }
    }

    public BridgeManager getBridgeManager() {
        if (bridgeManager == null) {
            bridgeManager = getActivity().bridgeManager;
        }
        return bridgeManager;
    }

    public void loadHtmlInWebView(final String url) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getActivity().webView.loadUrl(url);
            }
        });
    }

    public String getBaseURLForTestFiles() {
        return "file:///android_asset/thali/";
    }

    public CreateClientBuilder getCreateClientBuilder() {
        return new AndroidEktorpCreateClientBuilder();
    }

    public Context getContextWithNewSubdirectory() {
        return new ContextInTempDirectory(getActivity().getApplicationContext());
    }
}
