/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.operation.projectors.fetch;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import io.crate.core.collections.Bucket;
import io.crate.core.collections.Row;

import javax.annotation.Nullable;
import java.util.Iterator;

class ReaderBucket {

    private final boolean fetchRequired;
    final Object[] partitionValues;
    final IntObjectHashMap<Object[]> docs = new IntObjectHashMap<>();

    ReaderBucket(boolean fetchRequired, @Nullable Object[] partitionValues) {
        this.fetchRequired = fetchRequired;
        this.partitionValues = partitionValues;
    }

    void require(int doc) {
        docs.putIfAbsent(doc, null);
    }

    Object[] get(int doc) {
        return docs.get(doc);
    }

    void fetched(Bucket bucket) {
        assert bucket.size() == docs.size();
        Iterator<Row> rowIterator = bucket.iterator();

        for (IntCursor intCursor : docs.keys()) {
            docs.indexReplace(intCursor.index, rowIterator.next().materialize());
        }
        assert !rowIterator.hasNext();
    }

    boolean fetchRequired() {
        return fetchRequired;
    }
}