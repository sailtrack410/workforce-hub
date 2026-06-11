#!/usr/bin/env python3
"""OpenAI-compatible stub model server for local IM x Agent demos."""

from http.server import BaseHTTPRequestHandler, HTTPServer
import json


def read_request_body(handler):
    length = handler.headers.get("Content-Length")
    if length:
        return handler.rfile.read(int(length))
    if handler.headers.get("Transfer-Encoding", "").lower() == "chunked":
        chunks = []
        while True:
            size_line = handler.rfile.readline().strip()
            if not size_line:
                continue
            size = int(size_line.split(b";", 1)[0], 16)
            if size == 0:
                while handler.rfile.readline().strip():
                    pass
                break
            chunks.append(handler.rfile.read(size))
            handler.rfile.read(2)
        return b"".join(chunks)
    return b"{}"


def extract_last_user_text(payload):
    messages = payload.get("messages") or []
    for message in reversed(messages):
        if message.get("role") == "user":
            content = message.get("content") or ""
            if isinstance(content, list):
                parts = []
                for item in content:
                    if isinstance(item, dict):
                        parts.append(str(item.get("text") or item.get("content") or ""))
                    else:
                        parts.append(str(item))
                return "\n".join(parts).strip()
            return str(content).strip()
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


def build_intent_reply(user_text):
    input_text = user_text
    marker = "输入内容："
    if marker in user_text:
        input_text = user_text.split(marker, 1)[1].split("\n\n", 1)[0]
    if "KNOWLEDGE" in user_text and ("制度" in input_text or "知识库" in input_text or "政策" in input_text or "文档" in input_text):
        intent = "KNOWLEDGE"
    elif "OA" in user_text and ("假期" in input_text or "余额" in input_text or "年假" in input_text or "请假" in input_text or "审批" in input_text or "申请" in input_text):
        intent = "OA"
    elif "QA" in user_text:
        intent = "QA"
    elif "假期" in input_text or "余额" in input_text or "年假" in input_text:
        intent = "查询假期"
    elif "请假" in input_text or "申请" in input_text:
        intent = "申请请假"
    else:
        intent = "问答"
    return {"intent": intent}


def build_output_format_reply(user_text):
    if "真实业务字段" in user_text:
        return {
            "title": "年假余额查询",
            "answer": "您的年假余额为 2 天，其中可用天数 2 天，冻结天数 0 天。",
            "reason": "根据系统记录的假期数据"
        }
    if "知识库检索未接入真实索引" in user_text:
        return {
            "title": "知识库未命中",
            "answer": "当前知识库检索未接入真实索引，本次不返回引用。",
            "reason": "知识库数据源未接入"
        }
    return {
        "title": "普通问答",
        "answer": "这是普通问答回复，未查询业务数据库。",
        "reason": None
    }


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_response(404)
            self.end_headers()
            return

        raw = read_request_body(self)
        raw_text = raw.decode("utf-8") or "{}"
        payload = json.loads(raw_text)
        user_text = extract_last_user_text(payload)
        if "可选意图" in user_text:
            reply = json.dumps(build_intent_reply(user_text), ensure_ascii=False)
        elif "outputFormat" in user_text:
            reply = json.dumps(build_output_format_reply(user_text), ensure_ascii=False)
        else:
            reply = json.dumps(build_knowledge_answer_card(user_text), ensure_ascii=False)
        print("stub user_text=", user_text[:220].replace("\n", "\\n"), flush=True)
        print("stub reply=", reply[:220], flush=True)
        body = {
            "choices": [
                {
                    "message": {
                        "content": reply
                    },
                    "finish_reason": "stop"
                }
            ],
            "usage": {
                "prompt_tokens": 1,
                "completion_tokens": 1,
                "total_tokens": 2
            }
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
