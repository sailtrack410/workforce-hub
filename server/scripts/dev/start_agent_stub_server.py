#!/usr/bin/env python3
"""OpenAI-compatible stub model server for local IM x Agent demos."""

from http.server import BaseHTTPRequestHandler, HTTPServer
import json


def extract_last_user_text(payload):
    messages = payload.get("messages") or []
    for message in reversed(messages):
        if message.get("role") == "user":
            return (message.get("content") or "").strip()
    return ""


def build_knowledge_answer_card(user_text):
    return {
        "cardType": "knowledge_answer",
        "state": "ANSWERED",
        "title": "年假与考勤影响",
        "answer": "可以申请。审批通过后系统会按请假时间扣减年假余额，并将对应日期的考勤日结果标记为请假，不再按缺卡异常处理。",
        "confidence": "high",
        "generatedAt": "15:24",
        "missingReason": "命中 3 条制度片段，最高相似度 87%",
        "relatedBusiness": [
            {"label": "当前余额", "value": "6.5 天", "tone": "green"},
            {"label": "申请时长", "value": "2 天", "tone": "blue"},
            {"label": "审批路径", "value": "直属上级", "tone": "amber"},
        ],
        "relatedFlows": [
            {
                "id": "WF-20260514-001",
                "title": "请假申请 WF-20260514-001",
                "status": "审批中",
                "statusTone": "primary",
                "summary": "点击后调用员工端审批详情与轨迹接口，展示申请字段、附件和动作记录。",
                "fields": [
                    ["问题", user_text or "帮我总结今天待办"],
                    ["当前节点", "直属上级"],
                    ["接口", "/api/employee/workflow/requests/{requestId}"],
                ],
                "apiRef": {
                    "status": "available",
                    "method": "GET",
                    "path": "/api/employee/workflow/requests/{requestId}",
                    "description": "流程详情",
                },
            }
        ],
        "citations": [
            {
                "id": "policy-annual-leave",
                "title": "员工年假制度",
                "sourceType": "POLICY",
                "score": 0.87,
                "snippet": "年假申请通过后，系统按审批单时间扣减余额，并同步生成请假考勤结果。",
                "apiRef": {
                    "status": "planned",
                    "method": "GET",
                    "path": "/api/employee/knowledge/spaces/{spaceId}/documents/{documentId}",
                    "description": "打开知识库原文与命中片段",
                },
            }
        ],
        "suggestedActions": [
            {
                "label": "打开申请",
                "action": "open_flow",
                "apiRef": {
                    "status": "available",
                    "method": "GET",
                    "path": "/api/employee/workflow/requests/{requestId}",
                },
            },
            {
                "label": "查看引用",
                "action": "open_citation",
                "apiRef": {
                    "status": "planned",
                    "method": "GET",
                    "path": "/api/employee/knowledge/chunks/{chunkId}",
                },
            },
        ],
    }


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_response(404)
            self.end_headers()
            return

        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length > 0 else b"{}"
        payload = json.loads(raw.decode("utf-8") or "{}")
        user_text = extract_last_user_text(payload)
        reply = json.dumps(build_knowledge_answer_card(user_text), ensure_ascii=False)
        body = {
            "choices": [
                {
                    "message": {
                        "content": reply
                    }
                }
            ]
        }
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, fmt, *args):
        return


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", 18080), Handler)
    print("Agent stub listening on http://127.0.0.1:18080/v1/chat/completions")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
