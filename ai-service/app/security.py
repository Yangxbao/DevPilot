"""内部调用鉴权：Java 后端持有 X-Internal-Token，直接暴露的端口拒绝外部请求。"""
import fastapi

from .config import get_settings


def require_internal_token(x_internal_token: str = fastapi.Header(default="")) -> None:
    settings = get_settings()
    if not settings.internal_token or x_internal_token != settings.internal_token:
        raise fastapi.HTTPException(status_code=401, detail="invalid internal token")
