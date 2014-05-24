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

import android.test.*;
import com.couchbase.lite.*;
import com.couchbase.lite.android.*;
import com.msopentech.thali.utilities.android.*;
import com.msopentech.thali.utilities.universal.*;

/**
 * We share tests between Java and Android but they require different base classes to get unit tests to work so we
 * hide this by having shared tests inherit from ThaliTestCase. This class also wraps environmental values that
 * differ between Java and Android.
 */
public class ThaliTestCase extends AndroidTestCase {

    /**
     * Returns the Couchbase context object
     * @return
     */
    public Context getCouchbaseContext() {
        return new AndroidContext(getContext());
    }

    public CreateClientBuilder getCreateClientBuilder() {
        return new AndroidEktorpCreateClientBuilder();
    }
}
