/*
 * MIT License
 *
 * Copyright (c) 2026 materin-tech
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.materin.gateway.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PendingMessageBuffer 单测：FIFO 顺序、有界丢最旧、drain 清空。
 */
class PendingMessageBufferTest {

    @Test
    @DisplayName("drain 按 FIFO 顺序返回全部消息并清空")
    void drainsInFifoOrderAndClears() {
        PendingMessageBuffer buffer = new PendingMessageBuffer(10);
        buffer.enqueue("t1", new byte[]{1});
        buffer.enqueue("t2", new byte[]{2});
        buffer.enqueue("t3", new byte[]{3});

        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.drain()).extracting(PendingMessageBuffer.PendingMessage::topic)
                .containsExactly("t1", "t2", "t3");
        assertThat(buffer.size()).isZero();
        assertThat(buffer.drain()).isEmpty();
    }

    @Test
    @DisplayName("容量满后丢弃最旧消息（保留最新）")
    void dropsOldestWhenCapacityExceeded() {
        PendingMessageBuffer buffer = new PendingMessageBuffer(2);
        buffer.enqueue("old-1", new byte[]{1});
        buffer.enqueue("old-2", new byte[]{2});
        buffer.enqueue("new-3", new byte[]{3});

        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.drain()).extracting(PendingMessageBuffer.PendingMessage::topic)
                .containsExactly("old-2", "new-3");
    }
}
