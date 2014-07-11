/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticspring.messaging;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import org.elasticspring.core.env.stack.StackResourceRegistry;
import org.elasticspring.core.support.documentation.RuntimeUse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class MessageListenerContainerAwsTest {

	private static final int BATCH_MESSAGE_SIZE = 10;

	private static final int TOTAL_BATCHES = 100;

	private static final int TOTAL_MESSAGES = BATCH_MESSAGE_SIZE * TOTAL_BATCHES;

	@SuppressWarnings("SpringJavaAutowiringInspection")
	@Autowired
	private AmazonSQSAsyncClient amazonSqsClient;

	@Autowired
	private TaskExecutor taskExecutor;

	@Autowired
	private MessageReceiver messageReceiver;

	@Autowired
	private StackResourceRegistry stackResourceRegistry;

	@Before
	public void insertTotalNumberOfMessagesIntoTheLoadTestQueue() throws InterruptedException {
		CountDownLatch countDownLatch = new CountDownLatch(TOTAL_BATCHES);

		for (int batch = 0; batch < TOTAL_BATCHES; batch++) {
			this.taskExecutor.execute(new QueueMessageSender(this.stackResourceRegistry.lookupPhysicalResourceId("LoadTestQueue"), this.amazonSqsClient, countDownLatch));
		}

		countDownLatch.await();
	}

	@Test
	public void listenToAllMessagesUntilTheyAreReceivedOrTimeOut() throws Exception {
		this.messageReceiver.getCountDownLatch().await(1, TimeUnit.MINUTES);
	}

	static class MessageReceiver {

		@RuntimeUse
		@MessageMapping("LoadTestQueue")
		public void onMessage(String message) {
			assertNotNull(message);
			this.getCountDownLatch().countDown();
		}

		CountDownLatch getCountDownLatch() {
			return this.countDownLatch;
		}

		private final CountDownLatch countDownLatch = new CountDownLatch(TOTAL_MESSAGES);


	}

	private static class QueueMessageSender implements Runnable {

		private final String queueUrl;
		private final AmazonSQS amazonSqs;
		private final CountDownLatch countDownLatch;

		private QueueMessageSender(String queueUrl, AmazonSQS amazonSqs, CountDownLatch countDownLatch) {
			this.queueUrl = queueUrl;
			this.amazonSqs = amazonSqs;
			this.countDownLatch = countDownLatch;
		}

		@Override
		public void run() {
			List<SendMessageBatchRequestEntry> messages = new ArrayList<SendMessageBatchRequestEntry>();
			for (int i = 0; i < BATCH_MESSAGE_SIZE; i++) {
				messages.add(new SendMessageBatchRequestEntry(Integer.toString(i), new StringBuilder().append("message_").append(i).toString()));
			}
			this.amazonSqs.sendMessageBatch(new SendMessageBatchRequest(this.queueUrl, messages));
			this.countDownLatch.countDown();
		}
	}
}