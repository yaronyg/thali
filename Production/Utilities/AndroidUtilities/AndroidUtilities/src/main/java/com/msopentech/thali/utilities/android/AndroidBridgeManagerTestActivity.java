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

package com.msopentech.thali.utilities.android;

import android.app.*;
import android.os.*;
import android.webkit.*;
import com.couchbase.lite.util.*;
import com.msopentech.thali.utilities.android.*;
import com.msopentech.thali.utilities.webviewbridge.*;
import com.msopentech.thali.utilities.xmlhttprequestbridge.*;

import java.io.*;
import java.net.*;

/**
 * Unfortunately we have to stick this test activity into production code because we can only launch the test
 * activity via the activity test framework if it's possible to send the activity an intent and so far the
 * only way to do that is to register the activity in the AndroidManifest.xml which will only accept registrations
 * from activities in production. I really wish Android had a mock Activity.
 */
public class AndroidBridgeManagerTestActivity extends Activity {
    public BridgeManager bridgeManager = null;
    public WebView webView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        //TODO: Oh, this is all a huge security hole.
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAppCacheEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e("xmlhttptest",
                        "errorCode: " + errorCode + ", description: " + description + ", failingUrl: " + failingUrl);
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.e("xmlhttptest", consoleMessage.message() + " - " + consoleMessage.messageLevel().toString() + " - "
                        + consoleMessage.lineNumber() + " - " + consoleMessage.sourceId());
                return false;
            }
        });

        bridgeManager = new AndroidBridgeManager(this, webView);
    }
}
