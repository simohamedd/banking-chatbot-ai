# Banking Chatbot AI

[![Build and test](https://github.com/simohamedd/banking-chatbot-ai/actions/workflows/build.yml/badge.svg)](https://github.com/simohamedd/banking-chatbot-ai/actions/workflows/build.yml)

A banking assistant you chat with in the terminal. It understands what you're asking with a naive Bayes text classifier written from scratch, picks out amounts and account numbers, and holds a short conversation to complete tasks safely.

## What it can do
- Tell you your balance and recent transactions
- Make a transfer, asking for the account or amount if you left them out, and confirming before money moves
- Block a lost or stolen card, and unblock it if it turns up, each after you confirm
- Give branch opening hours, and say hello and goodbye

## How it works
1. **Tokenizer:** lower-cases, removes filler words, replaces numbers with a `<num>` token and trims word endings.
2. **Classifier:** multinomial naive Bayes with Laplace smoothing, trained on about 100 example phrases in `src/main/resources/intents.txt`. Add a line there to teach it something new.
3. **Confidence:** if no intent is likely enough, the bot says it didn't understand instead of guessing.
4. **Entities:** regular expressions find amounts (`£20`, `20 pounds`, `20 quid`) and 8-digit account numbers, and saved payees can be named instead ("send £20 to Yusuf").
5. **Dialogue:** the bot remembers a half-finished transfer across turns and only acts on a clear "yes".

## Run
```bash
mvn -q compile exec:java
```

## Test
```bash
mvn test
```
Includes a leave-one-out test: each training phrase is held back in turn and the classifier must still get it right.

## Licence
MIT. See [LICENSE](LICENSE).
