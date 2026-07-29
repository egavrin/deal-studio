#!/usr/bin/env python3
"""Generate a balanced Russian corpus for the twelve production intents."""

import argparse
import json
from pathlib import Path
import random


INTENTS = (
    "get_current_time",
    "get_weather",
    "set_timer",
    "set_alarm",
    "create_reminder",
    "create_note",
    "calculate",
    "open_app",
    "help",
    "web_search",
    "web_research",
    "unknown",
)
PREFIXES = ("", "Пожалуйста, ", "Ассистент, ", "Можешь ")
SUFFIXES = ("", " пожалуйста", " сейчас", " для меня")


def record(text, intent, slots=()):
    item = {"text": text, "intent": intent}
    if slots:
        item["slots"] = []
        for name, value in slots:
            start = text.index(value)
            item["slots"].append(
                {"name": name, "value": value, "start": start, "end": start + len(value)}
            )
    return item


def variants(phrases):
    for phrase in phrases:
        for prefix in PREFIXES:
            for suffix in SUFFIXES:
                text = f"{prefix}{phrase}{suffix}".strip()
                yield text[0].upper() + text[1:]


def slotted(rows, intent, values, templates, slot_name):
    for value in values:
        for template in templates:
            text = template.format(value=value)
            rows.append(record(text, intent, ((slot_name, value),)))


def generate():
    rows = []
    rows.extend(
        record(text, "get_current_time")
        for text in variants(
            (
                "сколько времени",
                "который сейчас час",
                "скажи текущее время",
                "покажи время",
                "сколько сейчас времени в телефоне",
            )
        )
    )
    rows.extend(
        record(text, "get_weather")
        for text in variants(
            (
                "какая погода сегодня",
                "покажи погоду",
                "что с погодой",
                "нужен прогноз на сегодня",
                "будет ли дождь",
            )
        )
    )
    slotted(
        rows,
        "get_weather",
        (
            "Москве", "Санкт-Петербурге", "Казани", "Самаре", "Новосибирске",
            "Сочи", "Туле", "Омске", "Уфе", "Перми",
        ),
        (
            "Какая погода в {value}", "Покажи погоду в {value}", "Что с погодой в {value}",
            "Сколько градусов в {value}", "Нужен прогноз для города {value}",
            "Будет ли дождь в {value}", "Как погода в {value} сегодня",
            "Какая погода в {value} сегодня", "Какая сегодня погода в {value}",
            "Покажи прогноз в {value} на сегодня", "Погода в {value} сегодня",
            "Расскажи о погоде в {value}",
        ),
        "location",
    )
    slotted(
        rows,
        "set_timer",
        (
            "одну минуту", "две минуты", "три минуты", "пять минут", "десять минут",
            "пятнадцать минут", "двадцать минут", "полчаса", "один час", "два часа",
        ),
        (
            "Поставь таймер на {value}", "Запусти таймер на {value}", "Засеки {value}",
            "Создай таймер длительностью {value}", "Таймер на {value}", "Отсчитай {value}",
            "Включи таймер на {value}", "Мне нужен таймер на {value}",
        ),
        "duration",
    )
    rows.extend(
        record(text, "set_timer")
        for text in variants(("поставь таймер", "запусти таймер", "мне нужен таймер"))
    )
    slotted(
        rows,
        "set_alarm",
        ("06:30", "07:00", "07:30", "08:00", "08:15", "09:00", "10:45", "18:20", "21:00", "23:10"),
        (
            "Поставь будильник на {value}", "Разбуди меня в {value}",
            "Заведи будильник на {value}", "Установи будильник на завтра в {value}",
            "Будильник на {value}", "Мне нужно проснуться в {value}",
            "Создай будильник в {value}", "Поставь сигнал на {value}",
        ),
        "time",
    )
    rows.extend(
        record(text, "set_alarm")
        for text in variants(("поставь будильник", "разбуди меня завтра", "создай будильник"))
    )
    slotted(
        rows,
        "create_reminder",
        (
            "проверить духовку", "позвонить маме", "забрать заказ", "оплатить интернет",
            "полить цветы", "выключить плиту", "купить корм", "отправить отчет",
            "принять лекарство", "зарядить телефон",
        ),
        (
            "Напомни через час {value}", "Напомни завтра {value}",
            "Создай напоминание {value}", "Поставь напоминание на вечер: {value}",
            "Не дай забыть {value}", "Напомни мне {value} через два часа",
            "Запланируй напоминание {value}", "Хочу получить напоминание: {value}",
        ),
        "reminder_text",
    )
    rows.extend(
        record(text, "create_reminder")
        for text in variants(("создай напоминание", "напомни мне", "поставь напоминание"))
    )
    slotted(
        rows,
        "create_note",
        (
            "купить молоко", "код от двери 2548", "идея для презентации", "проверить билеты",
            "рецепт сырников", "номер заказа 418", "встреча у метро", "список книг на лето",
            "позвонить в сервис", "адрес новой кофейни",
        ),
        (
            "Запиши заметку {value}", "Создай заметку {value}", "Сохрани как заметку: {value}",
            "Запиши для меня {value}", "Новая заметка: {value}", "Добавь заметку {value}",
            "Хочу сохранить заметку {value}", "Зафиксируй в заметке {value}",
        ),
        "text",
    )
    rows.extend(
        record(text, "create_note")
        for text in variants(("создай заметку", "запиши заметку", "добавь заметку"))
    )
    for spoken, symbol in (
        ("плюс", "+"), ("минус", "-"), ("умножить на", "*"), ("разделить на", "/")
    ):
        for left, right in ((18, 3), (125, 37), (81, 9), (42, 7), (15, 6)):
            for expression in (f"{left} {spoken} {right}", f"{left} {symbol} {right}"):
                for template in (
                    "Сколько будет {value}", "Посчитай {value}",
                    "Вычисли {value}", "Реши пример {value}",
                ):
                    text = template.format(value=expression)
                    rows.append(record(text, "calculate", (("expression", expression),)))
    rows.extend(
        record(text, "calculate")
        for text in variants(("посчитай", "реши пример", "что нужно вычислить"))
    )
    slotted(
        rows,
        "open_app",
        (
            "Telegram", "YouTube", "Камера", "Карты", "Калькулятор",
            "Настройки", "Галерея", "Chrome", "Музыка", "Почта",
        ),
        (
            "Открой {value}", "Запусти {value}", "Открой приложение {value}",
            "Перейди в {value}", "Хочу открыть {value}", "Покажи приложение {value}",
            "Включи {value}", "Запусти мне {value}",
        ),
        "app_name",
    )
    rows.extend(
        record(text, "open_app")
        for text in variants(("открой приложение", "запусти приложение", "что открыть"))
    )
    rows.extend(
        record(text, "help")
        for text in variants(
            (
                "помощь", "что ты умеешь", "покажи доступные команды",
                "как тобой пользоваться", "какие команды поддерживаются",
            )
        )
    )
    rows.extend(
        record(text, "web_search")
        for text in variants(
            (
                "найди свежие новости про Android",
                "что нового в Android 17",
                "кто сейчас руководит компанией OpenAI",
                "проверь актуальный курс доллара",
                "найди последние данные об инфляции",
                "что произошло сегодня в мире технологий",
                "поищи в интернете официальную документацию Kotlin",
                "проверь свежие результаты Формулы-1",
            )
        )
    )
    rows.extend(
        record(text, "web_research")
        for text in variants(
            (
                "исследуй рынок локальных голосовых ассистентов",
                "проведи исследование конкурентов Perplexity",
                "сравни по источникам современные модели распознавания речи",
                "собери подробный обзор новых функций Android",
                "проанализируй рынок складных смартфонов",
                "найди и сопоставь подходы к локальному синтезу речи",
                "изучи вопрос глубоко и подготовь выводы",
                "проверь несколько источников и составь исследование",
            )
        )
    )
    rows.extend(
        record(text, "unknown")
        for text in variants(
            (
                "почему небо синее", "объясни квантовую запутанность простыми словами",
                "как подготовиться к собеседованию", "придумай идею для подарка",
                "чем арабика отличается от робусты", "расскажи историю Санкт-Петербурга",
                "как работает солнечная батарея", "почему листья осенью желтеют",
                "составь короткий план тренировки", "что почитать о космосе",
                "как лучше учить английские слова", "объясни теорию относительности",
                "помоги придумать название проекта", "какие бывают стили архитектуры",
                "расскажи о пользе сна", "как устроен электромобиль",
                "почему море солёное", "что такое машинное обучение",
                "как выбрать велосипед", "придумай поздравление другу",
            )
        )
    )
    unique = {(item["text"], item["intent"]): item for item in rows}
    result = list(unique.values())
    random.Random(7).shuffle(result)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("training/rubert/synthetic_intents.jsonl"))
    args = parser.parse_args()
    rows = generate()
    counts = {intent: 0 for intent in INTENTS}
    for item in rows:
        counts[item["intent"]] += 1
    if min(counts.values()) < 80:
        raise RuntimeError(f"Dataset is not balanced enough: {counts}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "".join(json.dumps(item, ensure_ascii=False, sort_keys=True) + "\n" for item in rows),
        encoding="utf-8",
    )
    print(f"wrote {len(rows)} rows to {args.output}: {counts}")


if __name__ == "__main__":
    main()
