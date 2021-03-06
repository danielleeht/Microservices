/*
 * Copyright 2013-2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://aws.amazon.com/asl/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.services.kinesis.connectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import com.amazonaws.services.kinesis.connectors.interfaces.IKinesisConnectorPipeline;
import com.amazonaws.services.kinesis.metrics.interfaces.IMetricsFactory;

public abstract class KinesisConnectorExecutorBase<T, U> implements Runnable {
    private static final Log LOG = LogFactory.getLog(KinesisConnectorExecutorBase.class);
    public boolean executeWorker;
    // Amazon Kinesis Client Library worker to process records
    protected Worker worker;

    /**
     * Initialize the Amazon Kinesis Client Library configuration and worker
     * 
     * @param kinesisConnectorConfiguration Amazon Kinesis connector configuration
     */
    protected void initialize(KinesisConnectorConfiguration kinesisConnectorConfiguration, String content) {
        initialize(kinesisConnectorConfiguration, null, content);
    }

    /**
     * Initialize the Amazon Kinesis Client Library configuration and worker with metrics factory
     * 
     * @param kinesisConnectorConfiguration Amazon Kinesis connector configuration
     * @param metricFactory would be used to emit metrics in Amazon Kinesis Client Library
     */
    protected void
            initialize(KinesisConnectorConfiguration kinesisConnectorConfiguration, IMetricsFactory metricFactory, String content) {
        KinesisClientLibConfiguration kinesisClientLibConfiguration =
                new KinesisClientLibConfiguration(kinesisConnectorConfiguration.APP_NAME,
                        kinesisConnectorConfiguration.KINESIS_INPUT_STREAM,
                        kinesisConnectorConfiguration.AWS_CREDENTIALS_PROVIDER,
                        kinesisConnectorConfiguration.WORKER_ID).withKinesisEndpoint(kinesisConnectorConfiguration.KINESIS_ENDPOINT)
                        .withFailoverTimeMillis(kinesisConnectorConfiguration.FAILOVER_TIME)
                        .withMaxRecords(kinesisConnectorConfiguration.MAX_RECORDS)
                        .withInitialPositionInStream(kinesisConnectorConfiguration.INITIAL_POSITION_IN_STREAM)
                        .withIdleTimeBetweenReadsInMillis(kinesisConnectorConfiguration.IDLE_TIME_BETWEEN_READS)
                        .withCallProcessRecordsEvenForEmptyRecordList(KinesisConnectorConfiguration.DEFAULT_CALL_PROCESS_RECORDS_EVEN_FOR_EMPTY_LIST)
                        .withCleanupLeasesUponShardCompletion(kinesisConnectorConfiguration.CLEANUP_TERMINATED_SHARDS_BEFORE_EXPIRY)
                        .withParentShardPollIntervalMillis(kinesisConnectorConfiguration.PARENT_SHARD_POLL_INTERVAL)
                        .withShardSyncIntervalMillis(kinesisConnectorConfiguration.SHARD_SYNC_INTERVAL)
                        .withTaskBackoffTimeMillis(kinesisConnectorConfiguration.BACKOFF_INTERVAL)
                        .withMetricsBufferTimeMillis(kinesisConnectorConfiguration.CLOUDWATCH_BUFFER_TIME)
                        .withMetricsMaxQueueSize(kinesisConnectorConfiguration.CLOUDWATCH_MAX_QUEUE_SIZE)
                        .withUserAgent(kinesisConnectorConfiguration.APP_NAME + ","
                                + kinesisConnectorConfiguration.CONNECTOR_DESTINATION + ","
                                + KinesisConnectorConfiguration.KINESIS_CONNECTOR_USER_AGENT)
                        .withRegionName(kinesisConnectorConfiguration.REGION_NAME);

        if (!kinesisConnectorConfiguration.CALL_PROCESS_RECORDS_EVEN_FOR_EMPTY_LIST) {
            LOG.warn("The false value of callProcessRecordsEvenForEmptyList will be ignored. It must be set to true for the bufferTimeMillisecondsLimit to work correctly.");
        }

        if (kinesisConnectorConfiguration.IDLE_TIME_BETWEEN_READS > kinesisConnectorConfiguration.BUFFER_MILLISECONDS_LIMIT) {
            LOG.warn("idleTimeBetweenReads is greater than bufferTimeMillisecondsLimit. For best results, ensure that bufferTimeMillisecondsLimit is more than or equal to idleTimeBetweenReads ");
        }

        // If a metrics factory was specified, use it.
        if (metricFactory != null) {
            worker =
                    new Worker(getKinesisConnectorRecordProcessorFactory(content),
                            kinesisClientLibConfiguration,
                            metricFactory);
        } else {
            worker = new Worker(getKinesisConnectorRecordProcessorFactory(content), kinesisClientLibConfiguration);
        }
        LOG.info(getClass().getSimpleName() + " worker created");
    }

    @Override
    public void run() {
        if(executeWorker){
            if (worker != null) {
                // Start Amazon Kinesis Client Library worker to process records
                LOG.info("Starting worker in " + getClass().getSimpleName());
                try {
                    worker.run();

                } catch (Throwable t) {
                    LOG.error(t);
                    throw t;
                } finally {
                    LOG.error("Worker " + getClass().getSimpleName() + " is not running.");
                }
            } else {
                throw new RuntimeException("Initialize must be called before run.");
            }
        }

    }

    /**
     * This method returns a {@link KinesisConnectorRecordProcessorFactory} that contains the
     * appropriate {@link IKinesisConnectorPipeline} for the Amazon Kinesis Enabled Application
     * 
     * @return a {@link KinesisConnectorRecordProcessorFactory} that contains the appropriate
     *         {@link IKinesisConnectorPipeline} for the Amazon Kinesis Enabled Application
     */
    public abstract KinesisConnectorRecordProcessorFactory<T, U> getKinesisConnectorRecordProcessorFactory(String content);
}
