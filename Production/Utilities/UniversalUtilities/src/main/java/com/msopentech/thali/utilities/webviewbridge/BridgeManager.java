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


package com.msopentech.thali.utilities.webviewbridge;

import com.couchbase.lite.util.*;
import org.apache.commons.io.*;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.*;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;

/**
 * Provides the infrastructure for the Webview Javascript Bridge that is common to both JavaFX and Android's WebViews.
 * This object is thread safe.
 */
public abstract class BridgeManager implements Bridge {
    protected String callbackManager = "window.thali_callback_manager";
    private String managerNameInJavascript = "ThaliBridgeManager0";
    public static final String pathToBridgeManagerJs = "BridgeManager.js";
    protected static File resourcesFileDirectory = null;
    protected final static String resourcesFileDirectoryName = "UniversalUtilitiesResourcesFile";


    protected ConcurrentHashMap<String, BridgeHandler> registeredHandlers = new ConcurrentHashMap<String, BridgeHandler>();

    /**
     * A useful utility for small streams. The stream will be closed by this method.
     *
     * Taken from http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
     * @param inputStream
     * @return Stream as a string
     */
    public static String turnUTF8InputStreamToString(InputStream inputStream) {
        try {
            Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Unzips a zip file presented as an input stream into the target directory
     * @param zipFileStream
     * @param directoryToUnzipTo
     */
    public void unZipToDirectory(InputStream zipFileStream, File directoryToUnzipTo) {
        if (directoryToUnzipTo.exists() == false && directoryToUnzipTo.mkdirs() == false) {
            throw new RuntimeException("Could not create directory to unzip to: " + directoryToUnzipTo.getAbsolutePath());
        }

        ZipInputStream zipInputStream = new ZipInputStream(zipFileStream);
        FileOutputStream fileOutputStream = null;
        try {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                File fileForZipEntry = new File(directoryToUnzipTo, zipEntry.getName());
                // In theory this call isn't needed since directories should show up before the files in them in the
                // zip stream but I'm being paranoid.
                if (fileForZipEntry.getParentFile().exists() == false && fileForZipEntry.getParentFile().mkdirs() == false) {
                    throw new RuntimeException("Could not create entry to unzip: " + fileForZipEntry.getAbsolutePath());
                }
                if (zipEntry.isDirectory() == false) {
                    // I really wanted to use FileUtils.copyInputStreamToFile here but unfortunately it always
                    // closes a stream when it is done and the design of zipInputStream which gets magically re-used
                    // for each entry makes that close call a bad idea.
                    byte[] buffer = new byte[1024];
                    int bufferLength;
                    fileOutputStream = new FileOutputStream(fileForZipEntry);
                    while ((bufferLength = zipInputStream.read(buffer)) > 0) {
                        fileOutputStream.write(buffer, 0, bufferLength);
                    }
                } else if (fileForZipEntry.mkdir() == false) {
                    throw new RuntimeException(
                            "Could not create directory in zip file: " + fileForZipEntry.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Ooops", e);
        } finally {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
                zipInputStream.close();
            } catch (IOException e) {
                Log.e("ThaliBridgeManager", "Got an exception in finally: " + e);
            }
        }
    }

    /**
     * Due to issues with handling resources in Jar files and webviews in Android (see this project's build.gradle
     * for details) we unpack all the resource files into a local directory. Clients can get the root of that
     * directory by calling this function.
     * @return
     */
    public abstract File getResourceFileRoot();

    /**
     * Executes the submitted Javascript string in the associated WebView. Note, the string is expected to be a
     * .js file.
     * @param javascript
     */
    public abstract void executeJavascript(final String javascript);

    /**
     * However this method is implemented it MUST be thread safe.
     * @param handlerName
     *
     */
    public void callBack(String handlerName, String jsonString) {
        String functionCall = callbackManager + "[\"" + handlerName + "\"]('" + StringEscapeUtils.escapeEcmaScript(jsonString) + "')";
        this.executeJavascript(functionCall);
    }

    public void register(BridgeHandler bridgeHandler) {
        if (registeredHandlers.putIfAbsent(bridgeHandler.getName(), bridgeHandler) != null) {
            throw new RuntimeException("Already have a handler registered with the given name");
        }
    }

    public void registerIfNameNotTaken(BridgeHandler bridgeHandler) {
        registeredHandlers.putIfAbsent(bridgeHandler.getName(), bridgeHandler);
    }

    /**
     * The method that will be called by the Bridge framework from Javascript. E.g. someone calls to the bridge in
     * Javascript and the bridge then marshals the call and calls across the bridge to this method.
     * @param handlerName
     * @param jsonString
     * @param successHandlerName
     * @param failureHandlerName
     */
    public void invokeHandler(String handlerName, String jsonString, String successHandlerName, String failureHandlerName) {
        BridgeCallBack bridgeCallBack = new BridgeCallBack(this, successHandlerName, failureHandlerName);

        BridgeHandler bridgeHandler = registeredHandlers.get(handlerName);

        if (bridgeHandler == null) {
            // We throw the error back to javascript so it can 'test' to see if certain interfaces have been
            // registered or not.
            bridgeCallBack.failureHandler("{\"failure\":\"No registered handler with the name" + handlerName +"\"}");
        }

        bridgeHandler.call(jsonString, bridgeCallBack);
    }

    /**
     * This is the variable name that will be bound to in Javascript to expose the bridge manager.
     * @return
     */
    public String getManagerNameInJavascript() {
        return this.managerNameInJavascript;
    }

//    /**
//     * A little utility used by the Android and Java code to create the directory to hold the extracted files
//     * from the resources zip and to handle the actual unzipping.
//     * @param containerForResourcesFileDirectory The location where the resource file directory should be created
//     */
//    protected void createResourcesFileDirectoryAndFillItUp(File containerForResourcesFileDirectory) {
//        if (resourcesFileDirectory == null) {
//            resourcesFileDirectory = new File(containerForResourcesFileDirectory, resourcesFileDirectoryName);
//            if (resourcesFileDirectory.exists()) {
//                try {
//                    FileUtils.deleteDirectory(resourcesFileDirectory);
//                } catch (IOException e) {
//                    throw new RuntimeException("Couldn't delete resources file directory", e);
//                }
//            }
//
//            if (resourcesFileDirectory.mkdirs() == false) {
//                throw new RuntimeException("Could not create directory to unpack resource files.");
//            }
//            unZipToDirectory(getClass().getResourceAsStream(pathToResourceFilesZip), resourcesFileDirectory);
//        }
//    }
}
