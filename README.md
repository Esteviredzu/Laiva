# Laiva 🚢

**Laiva** - это проект мессенджера, который использует обычную электронную почту (IMAP/SMTP) вместо централизованных серверов. Пока гиганты индустрии собирают ваши метаданные, Laiva просто пересылает письма, которые никто, кроме вас, не прочитает.

---

## 📥 Скачать (Download)
Первая альфа-версия (будет кусаться и баговать):  
**[👉 Скачать Laiva_v1.0.apk](https://github.com/Esteviredzu/Laiva/releases/latest)**

---

## 🛡 Приватность
Главная фишка проекта - полная независимость:
* **Сквозное шифрование (E2EE):** Сообщения шифруются ключами AES + RSA прямо на телефоне. Ни я (разработчик), ни провайдер, ни товарищ майор не смогут прочитать вашу переписку.
* **Свой сервер - свои правила:** Вы можете зарегистрироваться на моём сервере `reg.stupidsitec.mooo.com` или поднять свой собственный почтовый сервер. Приложению всё равно, оно будет работать везде, где есть IMAP.
* **Никакого сбора данных:** Приложение не просит ваш номер телефона и не сканирует контакты. Исходный код открыт - проверяйте.


### ⚠️ Честный список багов (Known Issues):
Разработчик не умеет программировать, поэтому:
* 🎵 Воспроизведение музыки может баговать.
* ✅ Галочки о новых сообщениях работают некорректно.
* Разработчик не умеет придумывать названия

---

## 🤖 Создайте своего Laiva-бота!

**[👉 Перейти к Laiva Bot Framework (Python)](https://github.com/Esteviredzu/Laiva-bot)**

В репозитории фреймворка вы найдете пример создания ботов.

Официальный бот погоды, пишите "погода Москва", получите погоду в Москве, либо "weather [город]":
```
laiva://contact?name=weather&email=weather%40stupidsitec.mooo.com&pubkey=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvx3fCPi6r%2BPc%2FpY02msjezcgQqw2WwBTk2GNyEDsaN8y0iZcIqzGdQLmzRbcrmea570jM0FxnHmVCdXAzkq6Cm3H1SAykjhV6wM1IBf%2BJ7eG0%2BIzYCQSbx7HRADFUJGUU4rElOJNuuVynGmpR%2FkOTewMGaboo9oABnmhTziWpJ9%2FTcdVPlxv8951rvisnVwYkYHKS11gBI4Hdbv97NftMKV7ktHaZyJF%2BpSVws3iJ%2FRbWFh%2Fla9E%2FsKlT6vya9TM%2BDCMIuqe5BAmpBZjP3zZMk%2BP%2Bskd4pMIpTnen%2FKQ0pc%2BVLVszOMyeTbeSEcsGt1CSFn2r4hv0Ne7XsADlpoufwIDAQAB
```

---


## 🛠 Стек технологий
* **UI:** Jetpack Compose + Material 3.
* **Database:** Room (SQLite) - всё хранится только у вас в телефоне.
* **Encryption:** AES/RSA.
* **Network:** JavaMail API.
<img width="1080" height="2400" alt="5" src="https://github.com/user-attachments/assets/67eb9893-2bd0-40ed-aaa1-4f5d80bd2607" />
<img width="1080" height="2400" alt="4" src="https://github.com/user-attachments/assets/860fc495-eb7b-4f7a-9aa2-67aa331176b8" />
<img width="1080" height="2399" alt="3" src="https://github.com/user-attachments/assets/b67daaa3-528f-401a-83cf-ae798d27b1c6" />
<img width="1080" height="2400" alt="2" src="https://github.com/user-attachments/assets/ef920678-6c25-45b7-80f0-602e1301acf7" />
<img width="1080" height="2400" alt="1" src="https://github.com/user-attachments/assets/6e72118b-e9af-4b87-8838-550e7733b149" />

---
Угостите разработчика чашечкой кофе =)

Bitcoin:
```
bc1qpfzea5dv2638q5f2xhyl8zw4s9zf0wjm5v7t48
```
Карта:
```
Ну кто знает может скинуть)
```
