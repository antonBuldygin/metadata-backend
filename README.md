# Metadata Backend (Low-Code Engine)

Бэкенд-часть платформы для создания масштабируемых веб-приложений (Low-code).
Приложение позволяет создавать таблицы и поля через веб-интерфейс, автоматически генерирует DDL-запросы для PostgreSQL, создает представления (Views) и предоставляет универсальный REST API для работы с данными.

## 🛠 Технологический стек
* **Java 17**
* **Spring Boot 4.0.0** (Spring Web, Spring Data JPA, Spring JDBC)
* **PostgreSQL 15** (порт `5433`)
* **Lombok** (для логирования и boilerplate-кода)
* **Maven**

## 🚀 Ключевые возможности
1. **Динамическая генерация схемы БД**: Создание физических таблиц (`CREATE TABLE`) и колонок (`ALTER TABLE ADD COLUMN`) на основе метаданных.
2. **Автоматические Views**: Для каждой таблицы создается вьюха (`v_table_name`), которая используется для чтения данных.
3. **Универсальный REST API**: Один контроллер (`DataController`) обслуживает CRUD-операции для всех созданных таблиц.
4. **Рекурсивные связи (Relations)**: Поддержка полей типа `RELATION` с автоматическим построением `LEFT JOIN` (в том числе для вложенных связей) для вывода читаемых текстовых значений вместо ID.
5. **Оптимистичные блокировки**: Защита от одновременного редактирования через системное поле `version`.
6. **Бизнес-правила на Java**: Возможность писать серверные правила через `@EntityTable` и интерфейс `EntityRule`.

## ⚙️ Настройка и запуск

### 1. Требования
* Установленная Java (JDK 17+)
* Установленный PostgreSQL (желательно 15 версии, порт `5433`)
* Maven

### 2. База данных
Создайте базу данных `demo_db`:
```sql
CREATE DATABASE demo_db;

3. Конфигурация
Проверьте файл src/main/resources/application.properties:

properties
spring.datasource.url=jdbc:postgresql://localhost:5433/demo_db
spring.datasource.username=postgres
spring.datasource.password=123
spring.jpa.hibernate.ddl-auto=update
4. Запуск
Запустите приложение через IntelliJ IDEA (класс MetadataBackendApplication) или через Maven:

bash
mvn spring-boot:run
Сервер будет доступен по адресу: http://localhost:8080

📂 Структура проекта
entity/ — сущности метаданных (TableDef, FieldDef, RuleDef).

repository/ — Spring Data JPA репозитории.

controller/ — REST контроллеры (TableController для метаданных, DataController для данных).

service/ — RuleRegistry (реестр бизнес-правил) и DbHelper (утилиты для работы с БД).

service/rules/ — интерфейсы и классы бизнес-правил (EntityRule, @EntityTable).

🔗 Основные эндпоинты
GET /api/tables — список всех созданных таблиц.

POST /api/tables — создание новой таблицы (генерирует DDL).

GET /api/tables/{id}/fields — список полей таблицы.

POST /api/tables/{id}/fields — создание поля (генерирует ALTER TABLE).

GET /api/data/{tableName} — получение записей (с автоматическим JOIN связей).

POST /api/data/{tableName} — создание записи.

PUT /api/data/{tableName}/{id} — обновление записи.

DELETE /api/data/{tableName}/{id} — удаление записи.