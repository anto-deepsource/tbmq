/**
 * Copyright © 2016-2020 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.service.mqtt.client.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionQueueFactory;
import org.thingsboard.mqtt.broker.util.BytesUtil;

import javax.annotation.PreDestroy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ClientSessionPersistenceServiceImpl implements ClientSessionPersistenceService {
    @Value("${queue.client-session.acknowledge-wait-timeout-ms}")
    private long ackTimeoutMs;

    private final TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto>> clientSessionProducer;
    private final ServiceInfoProvider serviceInfoProvider;

    public ClientSessionPersistenceServiceImpl(ClientSessionQueueFactory clientSessionQueueFactory, ServiceInfoProvider serviceInfoProvider) {
        this.clientSessionProducer = clientSessionQueueFactory.createProducer();
        this.serviceInfoProvider = serviceInfoProvider;
    }

    @Override
    public void persistClientSessionInfoAsync(String clientId, QueueProtos.ClientSessionInfoProto clientSessionInfoProto, BasicCallback callback) {
        log.trace("[{}] Persisting client session asynchronously - {}", clientId, clientSessionInfoProto);
        clientSessionProducer.send(generateRequest(clientId, clientSessionInfoProto), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                if (callback != null) {
                    callback.onSuccess();
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (callback != null) {
                    callback.onFailure(t);
                }
            }
        });
    }

    @Override
    public void persistClientSessionInfoSync(String clientId, QueueProtos.ClientSessionInfoProto clientSessionInfoProto) throws QueuePersistenceException {
        log.trace("[{}] Persisting client session synchronously - {}", clientId, clientSessionInfoProto);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch updateWaiter = new CountDownLatch(1);
        clientSessionProducer.send(generateRequest(clientId, clientSessionInfoProto), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                updateWaiter.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
                errorRef.getAndSet(t);
                updateWaiter.countDown();
            }
        });

        boolean waitSuccessful = false;
        try {
            waitSuccessful = updateWaiter.await(ackTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            errorRef.getAndSet(e);
        }
        Throwable error = errorRef.get();
        if (!waitSuccessful || error != null) {
            log.warn("[{}] Failed to update client session. Reason - {}.",
                    clientId, error != null ? error.getMessage() : "timeout waiting");
            if (error != null) {
                log.trace("Detailed error:", error);
            }
            if (!waitSuccessful) {
                throw new QueuePersistenceException("Timed out to update client session");
            } else {
                throw new QueuePersistenceException("Failed to update client session");
            }
        }
    }

    private TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto> generateRequest(String clientId, QueueProtos.ClientSessionInfoProto clientSessionInfoProto) {
        TbProtoQueueMsg<QueueProtos.ClientSessionInfoProto> request = new TbProtoQueueMsg<>(clientId, clientSessionInfoProto);
        request.getHeaders().put(ClientSessionConst.SERVICE_ID_HEADER, BytesUtil.stringToBytes(serviceInfoProvider.getServiceId()));
        return request;
    }

    @PreDestroy
    public void destroy() {
        clientSessionProducer.stop();
    }
}
