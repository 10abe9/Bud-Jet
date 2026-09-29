"""Request/response bodies. Field names are the app's contract (docs/backend-api.md)."""

from typing import Literal

from pydantic import BaseModel, Field, field_validator

MAX_MESSAGE_CHARS = 300
MAX_HISTORY_TURNS = 6
MAX_HISTORY_TEXT_CHARS = 1200
MAX_CATEGORY_CHARS = 60


class MonthSummary(BaseModel):
    month: str = Field(pattern=r"^\d{4}-\d{2}$")
    income: float = 0.0
    expense: float = 0.0
    expenseByCategory: dict[str, float] = Field(default_factory=dict, max_length=100)

    @field_validator("expenseByCategory")
    @classmethod
    def _trim_names(cls, value: dict[str, float]) -> dict[str, float]:
        return {name.strip()[:MAX_CATEGORY_CHARS]: amount for name, amount in value.items()}


class LimitSummary(BaseModel):
    category: str = Field(max_length=200)
    limit: float
    spentThisMonth: float = 0.0

    @field_validator("category")
    @classmethod
    def _trim_name(cls, value: str) -> str:
        return value.strip()[:MAX_CATEGORY_CHARS]


class SavingGoalSummary(BaseModel):
    target: float
    saved: float = 0.0
    deadline: str | None = Field(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$")


class BudgetSummary(BaseModel):
    currency: str = Field(min_length=1, max_length=8)
    months: list[MonthSummary] = Field(default_factory=list, max_length=12)
    limits: list[LimitSummary] = Field(default_factory=list, max_length=50)
    savingGoal: SavingGoalSummary | None = None


class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    text: str = Field(max_length=10_000)

    @field_validator("text")
    @classmethod
    def _truncate(cls, value: str) -> str:
        # Do not trust the app to have trimmed it.
        return value.strip()[:MAX_HISTORY_TEXT_CHARS]


class InsightsRequest(BaseModel):
    summary: BudgetSummary


class ChatRequest(BaseModel):
    summary: BudgetSummary
    message: str = Field(max_length=5_000)
    history: list[ChatTurn] = Field(default_factory=list, max_length=100)

    @field_validator("history")
    @classmethod
    def _recent_only(cls, value: list[ChatTurn]) -> list[ChatTurn]:
        return [turn for turn in value if turn.text][-MAX_HISTORY_TURNS:]


class VerifyRequest(BaseModel):
    packageName: str = Field(max_length=200)
    productId: str = Field(max_length=200)
    purchaseToken: str = Field(min_length=1, max_length=4096)


class VerifyResponse(BaseModel):
    active: bool
    expiresAt: int | None


class Insight(BaseModel):
    title: str
    text: str


class InsightsResponse(BaseModel):
    insights: list[Insight]


class ChatResponse(BaseModel):
    reply: str
