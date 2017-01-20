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

package io.crate.metadata;

import com.google.common.annotations.VisibleForTesting;
import io.crate.Constants;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateUpdateTask;
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;

/**
 * Service that ensures a "crate_defaults" template exists.
 *
 * This template matches all indices and customizes dynamic mapping updates:
 *
 *  - dynamically added strings use docValues
 */
public class DefaultTemplateService {

    final static String TEMPLATE_NAME = "crate_defaults";

    private final static ESLogger LOGGER = Loggers.getLogger(DefaultTemplateService.class);
    private final static String DEFAULT_MAPPING_SOURCE = createDefaultMappingSource();
    private final ClusterService clusterService;


    public DefaultTemplateService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    public void createIfNotExists(ClusterState state) {
        if (clusterService.localNode().isMasterNode() == false) {
            return;
        }
        if (state.getMetaData().getTemplates().containsKey(TEMPLATE_NAME) == false) {
            createDefaultTemplate();
        }
    }

    private void createDefaultTemplate() {
        clusterService.submitStateUpdateTask("ensure-default-template", new ClusterStateUpdateTask() {
            @Override
            public ClusterState execute(ClusterState currentState) throws Exception {
                return addDefaultTemplate(currentState);
            }

            @Override
            public void onFailure(String source, Throwable cause) {
                LOGGER.error("Error during ensure-default-template source={}", cause, source);
            }
        });
    }

    @VisibleForTesting
    static ClusterState addDefaultTemplate(ClusterState currentState) throws IOException {
        MetaData currentMetaData = currentState.getMetaData();
        ImmutableOpenMap<String, IndexTemplateMetaData> currentTemplates = currentMetaData.getTemplates();
        IndexTemplateMetaData defaultTemplate = currentTemplates.get(TEMPLATE_NAME);
        if (defaultTemplate != null) {
            return currentState;
        }

        ImmutableOpenMap<String, IndexTemplateMetaData> newTemplates = createCopyWithDefaultTemplateAdded(currentTemplates);
        MetaData.Builder mdBuilder = MetaData.builder(currentMetaData).templates(newTemplates);
        return ClusterState.builder(currentState).metaData(mdBuilder).build();
    }

    private static ImmutableOpenMap<String, IndexTemplateMetaData> createCopyWithDefaultTemplateAdded(
        ImmutableOpenMap<String, IndexTemplateMetaData> currentTemplates) throws IOException {

        IndexTemplateMetaData defaultTemplate = IndexTemplateMetaData.builder(TEMPLATE_NAME)
            .order(0)
            .putMapping(Constants.DEFAULT_MAPPING_TYPE, DEFAULT_MAPPING_SOURCE)
            .template("*")
            .build();
        ImmutableOpenMap.Builder<String, IndexTemplateMetaData> builder = ImmutableOpenMap.builder(currentTemplates);
        builder.put(TEMPLATE_NAME, defaultTemplate);
        return builder.build();
    }

    private static String createDefaultMappingSource() {
        try {
            // @formatter:off
            XContentBuilder builder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject(Constants.DEFAULT_MAPPING_TYPE)
                    .startArray("dynamic_templates")
                        .startObject()
                            .startObject("strings")
                                .field("match_mapping_type", "string")
                                .startObject("mapping")
                                    .field("type", "string")
                                    .field("doc_values", true)
                                    .field("index", "not_analyzed")
                                    .field("store", false)
                                .endObject()
                            .endObject()
                        .endObject()
                    .endArray()
                .endObject()
                .endObject();
            // @formatter:on

            return builder.bytes().toUtf8();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
