package it.com.jashmore.sqs.examples.integrationtests;

import static it.com.jashmore.sqs.examples.integrationtests.SqsListenerExampleJunit4IntegrationTest.QUEUE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.google.common.collect.ImmutableList;

import com.jashmore.sqs.examples.integrationtests.IntegrationTestExampleApplication;
import com.jashmore.sqs.test.LocalSqsExtension;
import com.jashmore.sqs.util.LocalSqsAsyncClient;
import com.jashmore.sqs.util.SqsQueuesConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(classes = {
        IntegrationTestExampleApplication.class,
        SqsListenerExampleJunit5IntegrationTest.TestConfiguration.class
}, webEnvironment = RANDOM_PORT)
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {"sqs.queues.integrationTestingQueue=" + QUEUE_NAME})
class SqsListenerExampleJunit5IntegrationTest {
    static final String QUEUE_NAME = "testQueue";
    private static final int QUEUE_MAX_RECEIVE_COUNT = 3;
    private static final int VISIBILITY_TIMEOUT_IN_SECONDS = 2;

    @RegisterExtension
    static final LocalSqsExtension LOCAL_SQS = new LocalSqsExtension(ImmutableList.of(
            SqsQueuesConfig.QueueConfig.builder().queueName(QUEUE_NAME)
                    .maxReceiveCount(QUEUE_MAX_RECEIVE_COUNT)
                    .visibilityTimeout(VISIBILITY_TIMEOUT_IN_SECONDS)
                    .build()
    ));

    @Configuration
    public static class TestConfiguration {
        @Bean
        public LocalSqsAsyncClient localSqsAsyncClient() {
            return LOCAL_SQS.getLocalAmazonSqsAsync();
        }
    }

    @Autowired
    private LocalSqsAsyncClient localSqsAsyncClient;

    @MockBean
    private IntegrationTestExampleApplication.SomeService mockSomeService;

    @Test
    void messagesPlacedOntoQueueArePickedUpMessageListener() throws Exception {
        // arrange
        final CountDownLatch messageReceivedCountDownLatch = new CountDownLatch(1);
        doAnswer(invocationOnMock -> {
            messageReceivedCountDownLatch.countDown();
            return null;
        }).when(mockSomeService).run(anyString());

        // act
        localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "my message");
        messageReceivedCountDownLatch.await(VISIBILITY_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);

        // assert
        verify(mockSomeService).run("my message");
    }

    @Test
    void messageFailingToProcessWillBeProcessedAgain() throws Exception {
        // arrange
        final CountDownLatch messageReceivedCountDownLatch = new CountDownLatch(1);
        final AtomicBoolean processedMessageOnce = new AtomicBoolean();
        doAnswer(invocationOnMock -> {
            if (!processedMessageOnce.getAndSet(true)) {
                throw new RuntimeException("Expected Test Exception");
            }
            messageReceivedCountDownLatch.countDown();
            return null;
        }).when(mockSomeService).run(anyString());

        // act
        localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "my message");
        messageReceivedCountDownLatch.await(3 * VISIBILITY_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);

        // assert
        verify(mockSomeService, times(2)).run("my message");
    }

    @Test
    void messageThatContinuesToFailWillBePlacedIntoDlq() throws Exception {
        // arrange
        final CountDownLatch messageReceivedCountDownLatch = new CountDownLatch(QUEUE_MAX_RECEIVE_COUNT);
        final String queueUrl = localSqsAsyncClient.getQueueUrl(QUEUE_NAME);
        doAnswer(invocationOnMock -> {
            messageReceivedCountDownLatch.countDown();
            throw new RuntimeException("Expected Test Exception");
        }).when(mockSomeService).run(anyString());

        // act
        localSqsAsyncClient.sendMessageToLocalQueue(QUEUE_NAME, "my message");
        messageReceivedCountDownLatch.await(VISIBILITY_TIMEOUT_IN_SECONDS * (QUEUE_MAX_RECEIVE_COUNT + 1), TimeUnit.SECONDS);
        waitForMessageVisibilityToExpire();

        // assert
        final GetQueueAttributesResponse queueAttributesResponse = localSqsAsyncClient.getQueueAttributes(builder -> builder
                .queueUrl(queueUrl + "-dlq")
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
        ).get();
        assertThat(queueAttributesResponse.attributes()).containsEntry(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "1");
    }

    private void waitForMessageVisibilityToExpire() throws InterruptedException {
        Thread.sleep(3000);
    }
}
