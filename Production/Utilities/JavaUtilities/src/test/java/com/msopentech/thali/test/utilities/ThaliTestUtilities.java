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


package com.msopentech.thali.test.utilities;

import com.msopentech.thali.CouchDBListener.*;
import com.msopentech.thali.utilities.universal.*;
import org.ektorp.*;
import org.ektorp.support.*;

import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.logging.*;

/**
 * These are utilities used to run tests in different projects.
 */
public class ThaliTestUtilities {

    public static final String TestDatabaseName = "test";
    public static final Random random = new Random();

    /**
     * Turns on various logging interfaces for Apache HTTP Client including the use of SimpleLog
     */
    public static void configuringLoggingApacheClient() {
        // According to http://stackoverflow.com/questions/3246792/how-to-enable-logging-for-apache-commons-httpclient-on-android
        // the following two lines are needed on Android. They aren't needed in general Java.
        Logger.getLogger("org.apache.http.wire").setLevel(Level.FINEST);
        Logger.getLogger("org.apache.http/headers").setLevel(Level.FINEST);

        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");

        System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");

        System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire.header", "debug");

        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.commons.httpclient", "debug");

        System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "all");
    }

    /**
     * We have to write tests that can run in both Android and Java so we have to invent our own
     * assert.
     * @param result
     */
    public static void assertFail(Boolean result) {
        if (result != true) {
            throw new RuntimeException();
        }
    }


    /**
     * Checks if the docs exist in the database. Note that the DocType implements an overload for equals that
     * will properly compare instances of that class.
     * @param couchDbConnector
     * @param docsToCheck
     */
    public static void validateDatabaseState(CouchDbConnector couchDbConnector, Collection<CouchDbDocument> docsToCheck) {
        List<String> docIds = couchDbConnector.getAllDocIds();

        assertFail(docIds.size() == docsToCheck.size());

        for(CouchDbDocument doc : docsToCheck) {
            assertFail(docIds.contains(doc.getId()));
            CouchDbDocument remoteDocument = couchDbConnector.get(doc.getClass(), doc.getId());
            assertFail(doc.equals(remoteDocument));
        }
    }

    /**
     * Compares documents in two databases to see if they are equal
     * @param database1
     * @param database2
     */
    public static void validateDatabaseEquality(CouchDbConnector database1, CouchDbConnector database2) {
        List<String> docIdsDB1 = database1.getAllDocIds();
        List<String> docIdsDB2 = database2.getAllDocIds();

        if (docIdsDB1.size() != docIdsDB2.size()) {
            throw new RuntimeException();
        }

        for(String docIdDB1 : docIdsDB1) {
            CouchDBDocumentBlogClassForTests docDB1 = database1.get(CouchDBDocumentBlogClassForTests.class, docIdDB1);
            CouchDBDocumentBlogClassForTests docDB2 = database2.get(CouchDBDocumentBlogClassForTests.class, docIdDB1);
            if (docDB1.equals(docDB2) == false) {
                throw new RuntimeException();
            }
        }
    }

    /**
     * Deletes a test database name and then fills it with random records of type TestBlogClass and
     * returns the records so they can be tested against. Note that it is possible for 0 docs to be
     * generated which can be useful for some kinds of tests and surprising for others.
     * @param couchDbInstance
     * @param databaseName
     * @param  minimumTestRecords
     * @param maximumTestRecords
     * @param clientPublicKey This can be null if we are doing regression testing of no SSL and SSL without client auth scenarios
     */
    public static List<CouchDbDocument> setUpData(CouchDbInstance couchDbInstance,
                                                  String databaseName, int minimumTestRecords, int maximumTestRecords,
                                                  PublicKey clientPublicKey) throws InvalidKeySpecException, NoSuchAlgorithmException {
        couchDbInstance.deleteDatabase(databaseName);
        couchDbInstance.createDatabase(databaseName);
        CouchDbConnector couchDbConnector = couchDbInstance.createConnector(databaseName, false);

        ArrayList<CouchDbDocument> generatedDocs = new ArrayList<CouchDbDocument>();

        int numberOfDocuments = random.nextInt((maximumTestRecords - minimumTestRecords)+1) + minimumTestRecords;
        for(int i = 0; i < numberOfDocuments; ++i) {
            GenerateDoc(couchDbConnector, generatedDocs);
        }

        return generatedDocs;
    }

    /**
     * Creates a single test doc
     * @param couchDbConnector
     * @return
     */
    public static CouchDBDocumentBlogClassForTests GenerateDoc(CouchDbConnector couchDbConnector) {
        String bigString = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String littleString = "1234567890";
        CouchDBDocumentBlogClassForTests testBlog = new CouchDBDocumentBlogClassForTests();
        testBlog.setBlogArticleName(String.valueOf(random.nextBoolean() ? bigString : littleString));
        testBlog.setBlogArticleContent(String.valueOf(random.nextBoolean() ? bigString : littleString));
        couchDbConnector.create(testBlog);
        return testBlog;
    }

    /**
     * Creates a single test doc and adds it to the generatedDocs
     * @param couchDbConnector
     * @param generatedDocs
     */
    public static void GenerateDoc(CouchDbConnector couchDbConnector, Collection<CouchDbDocument> generatedDocs) {
        generatedDocs.add(GenerateDoc(couchDbConnector));
    }

    public static void AttachToRandomDoc(CouchDbConnector couchDbConnector) {
        throw new RuntimeException();
    }

    /**
     * Deletes a random document in the database, if the database is empty then this is a NOOP.
     * @param couchDbConnector
     */
    public static void DeleteDoc(CouchDbConnector couchDbConnector) {
        List<String> ids = couchDbConnector.getAllDocIds();
        if (ids.size() == 0) {
            return;
        }
        int indexOfDocToDelete = ids.size() == 1 ? 0 : random.nextInt(ids.size() - 1);
        String idOfDocToDelete = ids.get(indexOfDocToDelete);
        CouchDbDocument docToDelete = couchDbConnector.get(CouchDBDocumentBlogClassForTests.class, idOfDocToDelete);
        couchDbConnector.delete(docToDelete);
    }

    /**
     * Alters a random document in the database, if the database is empty then this is a NOOP.
     * @param couchDbConnector
     */
    public static void AlterDoc(CouchDbConnector couchDbConnector) {
        List<String> ids = couchDbConnector.getAllDocIds();
        if (ids.size() == 0) {
            return;
        }
        int indexOfDocToAlter = ids.size() == 1 ? 0 : random.nextInt(ids.size() - 1);
        String idOfDocToAlter = ids.get(indexOfDocToAlter);
        CouchDBDocumentBlogClassForTests docToAlter = couchDbConnector.get(CouchDBDocumentBlogClassForTests.class, idOfDocToAlter);
        docToAlter.setBlogArticleName("234234234");
        docToAlter.setBlogArticleContent("jjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj");
        couchDbConnector.update(docToAlter);
    }

    public static void ResetKeyDatabaseAndPutInKey(CouchDbInstance couchDbInstance, PublicKey clientPublicKey) {
        couchDbInstance.deleteDatabase(ThaliListener.KeyDatabaseName);
        couchDbInstance.createDatabase(ThaliListener.KeyDatabaseName);
        ThaliClientToDeviceHubUtilities.configureKeyInServersKeyDatabase(clientPublicKey, couchDbInstance);
    }

}
