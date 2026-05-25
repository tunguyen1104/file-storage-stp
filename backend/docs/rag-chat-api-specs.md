# RAG Chat API Specs

## 1. Overview

RAG Chat APIs cho phép user tạo phiên chat, gửi câu hỏi RAG trên các tài liệu user có quyền truy cập, xem lại lịch sử message, đổi tên phiên chat và xóa phiên chat.

Base path:

```text
/api/v1/rag/chat/sessions
```

Tất cả API yêu cầu authentication giống các API còn lại của hệ thống. Backend lấy `userId`, `tenantId`, `role` từ security context, không nhận các field này từ request body.

## 2. Response Wrapper

### Success

```json
{
  "data": {},
  "success": true
}
```

`message` chỉ xuất hiện ở một số API khác; các API RAG chat hiện trả `data`.

### Error

```json
{
  "code": 1,
  "errors": "Invalid payload.",
  "success": false
}
```

Các lỗi phổ biến:

| HTTP | code | Ý nghĩa |
|---:|---:|---|
| 400 | 1 | Payload không hợp lệ, UUID không hợp lệ, validation fail |
| 401 | 2 | Chưa authenticated hoặc security context không hợp lệ |
| 403 | 3 | Không có quyền truy cập session hoặc session không active |
| 500 | 1500 | Lỗi ngoài dự kiến |

## 3. Common Models

### RagChatSessionResponse

```json
{
  "id": "17716daa-37fe-4358-8bcd-a1605e61487f",
  "title": "New chat",
  "status": "ACTIVE",
  "createdAt": "2026-05-24T03:43:40.156940Z",
  "updatedAt": "2026-05-24T03:59:58.950350Z",
  "lastMessageAt": "2026-05-24T03:59:58.946773Z",
  "deletedAt": null
}
```

Field notes:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Session id |
| `title` | string | Tối đa 255 ký tự |
| `status` | enum | Hiện API chỉ list/load session `ACTIVE` |
| `createdAt` | instant | ISO-8601 UTC |
| `updatedAt` | instant | ISO-8601 UTC |
| `lastMessageAt` | instant/null | Thời điểm message gần nhất |
| `deletedAt` | instant/null | Hiện delete session là hard delete nên response delete không dùng field này |

### RagSourceResponse

```json
{
  "fileId": "dbc03eea-0f34-48ec-920f-5e294511ae53",
  "originalName": "Triết 2.pdf"
}
```

Field notes:

| Field | Type | Notes |
|---|---|---|
| `fileId` | UUID | File nguồn |
| `originalName` | string | Tên file nguồn |

API chỉ trả source ở mức file để frontend hiển thị. Các thông tin chi tiết theo chunk như `chunkIndex`, `snippet`, `score`, `sourceKey` vẫn được lưu nội bộ trong DB `rag_chat_message_sources`, nhưng không expose ra API.

Danh sách `sources` trong response được deduplicate theo `fileId`, giữ thứ tự source đầu tiên.

### RagChatMessageResponse

```json
{
  "id": "b7eb650f-a63f-4665-be30-0f2ddb7b4412",
  "role": "ASSISTANT",
  "content": "Nội dung câu trả lời...",
  "model": "llama-4-maverick",
  "retrievalTopK": 5,
  "inputTokens": 3668,
  "outputTokens": 661,
  "createdAt": "2026-05-24T04:37:54.410198Z",
  "sources": [
    {
      "fileId": "dbc03eea-0f34-48ec-920f-5e294511ae53",
      "originalName": "Triết 2.pdf"
    }
  ]
}
```

Field notes:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Message id |
| `role` | enum | `USER` hoặc `ASSISTANT` |
| `content` | string | Nội dung user hỏi hoặc assistant trả lời |
| `model` | string/null | Chỉ có ở assistant message |
| `retrievalTopK` | integer/null | Top K dùng khi retrieve; chỉ có ở assistant message |
| `inputTokens` | integer/null | Token input do model provider trả về nếu có |
| `outputTokens` | integer/null | Token output do model provider trả về nếu có |
| `createdAt` | instant | ISO-8601 UTC |
| `sources` | array | User message luôn là `[]`; assistant message chứa danh sách file nguồn dedupe |

Source metadata trong lịch sử chat là snapshot tại thời điểm assistant trả lời. API vẫn chỉ trả `fileId` và `originalName`; việc mở file, preview hoặc download file phải dùng API file tương ứng và vẫn bị check quyền hiện tại.

## 4. Create Chat Session

```http
POST /api/v1/rag/chat/sessions
Content-Type: application/json
```

Request body optional:

```json
{
  "title": "Policy questions"
}
```

Validation:

| Field | Rule |
|---|---|
| `title` | Optional, max 255 ký tự |

Nếu body null, title null hoặc blank, backend dùng title mặc định `New chat`.

Response: `201 Created`

```json
{
  "data": {
    "id": "17716daa-37fe-4358-8bcd-a1605e61487f",
    "title": "Policy questions",
    "status": "ACTIVE",
    "createdAt": "2026-05-24T03:43:40.156940Z",
    "updatedAt": "2026-05-24T03:43:40.156940Z",
    "lastMessageAt": null,
    "deletedAt": null
  },
  "success": true
}
```

## 5. List Chat Sessions

```http
GET /api/v1/rag/chat/sessions?page=1&size=20
```

Query params:

| Param | Type | Default | Notes |
|---|---:|---:|---|
| `page` | integer | 1 | Nếu nhỏ hơn 1 sẽ được normalize về 1 |
| `size` | integer | 20 | Nếu nhỏ hơn 1 sẽ về 1; tối đa 100 |

Behavior:

- Chỉ list session `ACTIVE`.
- Chỉ list session thuộc `tenantId` và `userId` hiện tại.
- Sort theo `updatedAt DESC`.

Response: `200 OK`

```json
{
  "data": {
    "data": [
      {
        "id": "17716daa-37fe-4358-8bcd-a1605e61487f",
        "title": "Policy questions",
        "status": "ACTIVE",
        "createdAt": "2026-05-24T03:43:40.156940Z",
        "updatedAt": "2026-05-24T03:59:58.950350Z",
        "lastMessageAt": "2026-05-24T03:59:58.946773Z",
        "deletedAt": null
      }
    ],
    "pageSize": 20,
    "totalPages": 1,
    "totalElements": 1,
    "currentPage": 1
  },
  "success": true
}
```

## 6. Rename Chat Session

```http
PATCH /api/v1/rag/chat/sessions/{sessionId}
Content-Type: application/json
```

Request:

```json
{
  "title": "Ladder notes"
}
```

Validation:

| Field | Rule |
|---|---|
| `title` | Required, not blank, max 255 ký tự |

Behavior:

- Chỉ rename session `ACTIVE`.
- User chỉ rename được session của chính mình.

Response: `200 OK`

```json
{
  "data": {
    "id": "17716daa-37fe-4358-8bcd-a1605e61487f",
    "title": "Ladder notes",
    "status": "ACTIVE",
    "createdAt": "2026-05-24T03:43:40.156940Z",
    "updatedAt": "2026-05-24T04:10:00.000000Z",
    "lastMessageAt": "2026-05-24T03:59:58.946773Z",
    "deletedAt": null
  },
  "success": true
}
```

## 7. Delete Chat Session

```http
DELETE /api/v1/rag/chat/sessions/{sessionId}
```

Behavior:

- Delete session là hard delete.
- Backend xóa theo thứ tự:
  1. `rag_chat_message_sources`
  2. `rag_chat_messages`
  3. `rag_chat_sessions`
- Sau khi delete, API lấy messages của session đó sẽ trả lỗi quyền/không truy cập được.

Response: `200 OK`

```json
{
  "data": {
    "id": "17716daa-37fe-4358-8bcd-a1605e61487f",
    "deleted": true
  },
  "success": true
}
```

## 8. Get Chat Messages

```http
GET /api/v1/rag/chat/sessions/{sessionId}/messages?page=1&size=50
```

Query params:

| Param | Type | Default | Notes |
|---|---:|---:|---|
| `page` | integer | 1 | Nếu nhỏ hơn 1 sẽ được normalize về 1 |
| `size` | integer | 50 | Nếu nhỏ hơn 1 sẽ về 1; tối đa 100 |

Behavior:

- Chỉ đọc được message của session `ACTIVE` thuộc user hiện tại.
- Message sort theo `createdAt ASC`.
- User message có `sources: []`.
- Assistant message trả `sources` là danh sách file nguồn dedupe theo `fileId`.
- Khi đọc lại lịch sử, backend trả metadata source đã lưu để phản ánh đúng nguồn đã từng được dùng cho answer cũ.
- Nếu user đã mất quyền file, frontend vẫn có thể thấy `fileId` và `originalName`, nhưng thao tác mở file/preview/download vẫn phải gọi API file và bị check quyền hiện tại.

Response: `200 OK`

```json
{
  "data": {
    "data": [
      {
        "id": "09d0b1ae-8d76-466d-a6b6-d5b3b97f6077",
        "role": "USER",
        "content": "Khái niệm bản chất và hiện tượng",
        "model": null,
        "retrievalTopK": null,
        "inputTokens": null,
        "outputTokens": null,
        "createdAt": "2026-05-24T04:37:39.889820Z",
        "sources": []
      },
      {
        "id": "b7eb650f-a63f-4665-be30-0f2ddb7b4412",
        "role": "ASSISTANT",
        "content": "Nội dung câu trả lời...",
        "model": "llama-4-maverick",
        "retrievalTopK": 5,
        "inputTokens": 3668,
        "outputTokens": 661,
        "createdAt": "2026-05-24T04:37:54.410198Z",
        "sources": [
          {
            "fileId": "dbc03eea-0f34-48ec-920f-5e294511ae53",
            "originalName": "Triết 2.pdf"
          }
        ]
      }
    ],
    "pageSize": 50,
    "totalPages": 1,
    "totalElements": 2,
    "currentPage": 1
  },
  "success": true
}
```

## 9. Send Chat Message

```http
POST /api/v1/rag/chat/sessions/{sessionId}/messages
Content-Type: application/json
```

Request:

```json
{
  "message": "Khái niệm bản chất và hiện tượng",
  "topK": 5
}
```

Validation:

| Field | Rule |
|---|---|
| `message` | Required, not blank, max theo `RAG_CHAT_MAX_MESSAGE_CHARS` default 8000 |
| `topK` | Optional, min 1, max theo `RAG_RETRIEVAL_MAX_TOP_K` default 20 |

Behavior:

- API synchronous, không streaming SSE.
- Backend lưu user message trước, retrieve RAG, gọi chat model, lưu assistant message và source snapshot.
- Nếu `topK` null, dùng `RAG_RETRIEVAL_TOP_K` default 8.
- Chat model dùng `RAG_CHAT_MODEL`.
- Retrieval dùng các file user có quyền truy cập tại thời điểm hỏi.
- Source trả ra frontend chỉ gồm file nguồn dedupe: `fileId`, `originalName`.
- Chi tiết chunk vẫn được lưu nội bộ trong `rag_chat_message_sources`.

Follow-up behavior:

- Một số câu ngắn kiểu `Chi tiết hơn`, `Ngắn gọn hơn`, `Tóm tắt lại`, `Giải thích thêm`, `Ví dụ`, `Tiếp tục` sẽ được nhận diện là follow-up.
- Với follow-up, backend dùng user question gần nhất trong history để build retrieval query.
- Điều kiện: `RAG_CHAT_MAX_HISTORY_MESSAGES` phải lớn hơn 0.
- Prompt trả lời vẫn giữ câu user hiện tại và history.

Response: `200 OK`

```json
{
  "data": {
    "session": {
      "id": "17716daa-37fe-4358-8bcd-a1605e61487f",
      "title": "Khái niệm bản chất và hiện tượng",
      "status": "ACTIVE",
      "createdAt": "2026-05-24T03:43:40.156940Z",
      "updatedAt": "2026-05-24T04:37:54.410198Z",
      "lastMessageAt": "2026-05-24T04:37:54.410198Z",
      "deletedAt": null
    },
    "userMessage": {
      "id": "09d0b1ae-8d76-466d-a6b6-d5b3b97f6077",
      "role": "USER",
      "content": "Khái niệm bản chất và hiện tượng",
      "model": null,
      "retrievalTopK": null,
      "inputTokens": null,
      "outputTokens": null,
      "createdAt": "2026-05-24T04:37:39.889820Z",
      "sources": []
    },
    "assistantMessage": {
      "id": "b7eb650f-a63f-4665-be30-0f2ddb7b4412",
      "role": "ASSISTANT",
      "content": "Nội dung câu trả lời...",
      "model": "llama-4-maverick",
      "retrievalTopK": 5,
      "inputTokens": 3668,
      "outputTokens": 661,
      "createdAt": "2026-05-24T04:37:54.410198Z",
      "sources": [
        {
          "fileId": "dbc03eea-0f34-48ec-920f-5e294511ae53",
          "originalName": "Triết 2.pdf"
        }
      ]
    }
  },
  "success": true
}
```

Nếu không tìm thấy context phù hợp:

```json
{
  "data": {
    "assistantMessage": {
      "role": "ASSISTANT",
      "content": "Toi khong tim thay thong tin phu hop trong cac tai lieu ban co quyen truy cap.",
      "model": "llama-4-maverick",
      "retrievalTopK": 5,
      "inputTokens": null,
      "outputTokens": null,
      "sources": []
    }
  },
  "success": true
}
```

Ví dụ trên đã lược bớt `session`, `userMessage`, `id`, `createdAt` để tập trung vào behavior; response thực tế vẫn có đầy đủ field như schema.

## 10. Frontend Notes

- Với màn hình list session, dùng `GET /sessions`.
- Khi mở một session, dùng `GET /sessions/{sessionId}/messages`.
- Khi gửi message, append optimistic user message nếu muốn, sau đó replace/append bằng response `userMessage` và `assistantMessage` từ API.
- Chỉ hiển thị source từ `assistantMessage.sources`.
- `sources` chỉ là danh sách file nguồn, đã dedupe theo `fileId`.
- Nên gọi messages `size=50` là mặc định để load đủ lịch sử chat, tránh phải gọi nhiều lần khi scroll.

## 11. Configs Liên Quan

| Env |             Default | Ý nghĩa |
|---|--------------------:|---|
| `RAG_CHAT_MODEL` | `alibaba-qwen3-32b` | Model chat |
| `RAG_RETRIEVAL_TOP_K` |                 `5` | Số chunk retrieve mặc định |
| `RAG_RETRIEVAL_MAX_TOP_K` |                `20` | `topK` tối đa user được truyền |
| `RAG_CHAT_MAX_HISTORY_MESSAGES` |                 `4` | Số message lịch sử đưa vào prompt |
| `RAG_CHAT_MAX_MESSAGE_CHARS` |              `8000` | Độ dài tối đa của user message |
| `RAG_VECTOR_CANDIDATES` |                `40` | Candidate count cho vector search |
| `RAG_BM25_CANDIDATES` |                `40` | Candidate count cho BM25 search |
