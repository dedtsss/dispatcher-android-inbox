#!/usr/bin/env python3
"""Fail-closed repository Actions-artifact budget check for upload steps."""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Iterable

DEFAULT_LIMIT_BYTES = 280_000_000
ARTIFACT_OVERHEAD_BYTES = 1_048_576
PAGE_SIZE = 100


class BudgetError(RuntimeError):
    pass


def list_active_artifacts(repo: str, token: str, api_url: str) -> tuple[int, int]:
    """Return (active bytes, active count), failing if pagination is not complete/stable."""
    base = api_url.rstrip("/")
    page = 1
    expected_total: int | None = None
    observed_total = 0
    seen_ids: set[int] = set()
    active_bytes = 0
    active_count = 0

    while True:
        url = f"{base}/repos/{repo}/actions/artifacts?per_page={PAGE_SIZE}&page={page}"
        request = urllib.request.Request(
            url,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.load(response)
        except (OSError, urllib.error.URLError, json.JSONDecodeError) as exc:
            raise BudgetError(f"GitHub artifact API недоступен; публикация закрыта: {exc}") from exc

        if not isinstance(payload, dict) or not isinstance(payload.get("artifacts"), list):
            raise BudgetError("GitHub API вернул некорректный список artifacts; публикация закрыта")
        total = payload.get("total_count")
        items = payload["artifacts"]
        if not isinstance(total, int) or total < 0:
            raise BudgetError("GitHub API не вернул достоверный total_count; публикация закрыта")
        if expected_total is None:
            expected_total = total
        elif total != expected_total:
            raise BudgetError("Состав artifacts изменился во время чтения страниц; публикация закрыта")
        if len(items) > PAGE_SIZE or (not items and observed_total < expected_total):
            raise BudgetError("GitHub API вернул неполную страницу artifacts; публикация закрыта")

        for item in items:
            if not isinstance(item, dict):
                raise BudgetError("Некорректная запись artifact; публикация закрыта")
            artifact_id = item.get("id")
            size = item.get("size_in_bytes")
            expired = item.get("expired")
            if not isinstance(artifact_id, int) or not isinstance(size, int) or size < 0 or not isinstance(expired, bool):
                raise BudgetError("Запись artifact неполная; публикация закрыта")
            if artifact_id in seen_ids:
                raise BudgetError("Страницы artifacts содержат повторный ID; публикация закрыта")
            seen_ids.add(artifact_id)
            if not expired:
                active_bytes += size
                active_count += 1

        observed_total += len(items)
        if observed_total >= expected_total:
            if observed_total != expected_total:
                raise BudgetError("Количество artifacts не совпало с total_count; публикация закрыта")
            return active_bytes, active_count
        if len(items) != PAGE_SIZE:
            raise BudgetError("GitHub API не вернул все страницы artifacts; публикация закрыта")
        page += 1


def candidate_size(paths: Iterable[str]) -> int:
    total = 0
    found = False
    for raw_path in paths:
        path = Path(raw_path)
        if not path.exists():
            continue
        found = True
        if path.is_file():
            total += path.stat().st_size
            continue
        for child in path.rglob("*"):
            if child.is_symlink():
                continue
            if child.is_file():
                total += child.stat().st_size
    if not found:
        raise BudgetError("Все заданные пути загрузки отсутствуют; публикация закрыта")
    return total


def verify_budget(
    repo: str,
    token: str,
    paths: Iterable[str],
    limit_bytes: int,
    api_url: str,
    archive_count: int = 1,
) -> tuple[int, int, int]:
    if not repo or "/" not in repo or not token:
        raise BudgetError("Нет репозитория или read-only токена GitHub; публикация закрыта")
    if limit_bytes <= 0 or archive_count <= 0:
        raise BudgetError("Неверный предел или число будущих архивов; публикация закрыта")
    existing, active_count = list_active_artifacts(repo, token, api_url)
    proposed = candidate_size(paths)
    projected = existing + proposed + archive_count * ARTIFACT_OVERHEAD_BYTES
    if projected > limit_bytes:
        raise BudgetError(
            f"Предел artifacts репозитория превышен: сейчас {existing} Б, кандидат {proposed} Б, "
            f"с запасом {archive_count * ARTIFACT_OVERHEAD_BYTES} Б = {projected} Б > {limit_bytes} Б; "
            "загрузка пропущена"
        )
    return existing, proposed, projected


def main(argv: list[str]) -> int:
    args = list(argv)
    archive_count = 1
    paths: list[str] = []
    while args:
        item = args.pop(0)
        if item == "--archive-count":
            if not args:
                print("--archive-count требует значение", file=sys.stderr)
                return 2
            try:
                archive_count = int(args.pop(0))
            except ValueError:
                print("--archive-count должен быть целым числом", file=sys.stderr)
                return 2
        else:
            paths.append(item)
    try:
        limit = int(os.environ.get("ACTIONS_ARTIFACT_BUDGET_BYTES", str(DEFAULT_LIMIT_BYTES)))
        existing, proposed, projected = verify_budget(
            os.environ.get("GITHUB_REPOSITORY", ""),
            os.environ.get("GH_TOKEN", os.environ.get("GITHUB_TOKEN", "")),
            paths,
            limit,
            os.environ.get("GITHUB_API_URL", "https://api.github.com"),
            archive_count,
        )
    except (ValueError, BudgetError) as exc:
        print(str(exc), file=sys.stderr)
        return 1
    message = (
        f"Локальный предел репозитория соблюдён: artifacts {existing} Б, кандидат {proposed} Б, "
        f"прогноз с запасом {projected} Б из {limit} Б. Это проверка текущего репозитория, "
        "не общей квоты аккаунта."
    )
    print(message)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as output:
            output.write(f"\n{message}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
