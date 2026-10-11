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

import { requestClient } from '#/api/request';

/** 文件管理 API（全平台经 StorageClient 上传的文件登记） */
export namespace FileApi {
  export interface SysFile {
    id?: number;
    fileName: string;
    bizType: string;
    bizRefId?: number;
    bucket?: string;
    storageKey: string;
    contentType?: string;
    size?: number;
    createTime?: string;
  }
}

async function getFiles(params: Record<string, any>) {
  return requestClient.get<{ items: FileApi.SysFile[]; total: number }>('/file/list', { params });
}

async function getFileDownloadUrl(id: number, expireSeconds = 3600) {
  return requestClient.get<{ url: string }>(`/file/${id}/download-url`, {
    params: { expireSeconds },
  });
}

async function deleteFile(id: number) {
  return requestClient.delete(`/file/${id}`);
}

export { deleteFile, getFileDownloadUrl, getFiles };
