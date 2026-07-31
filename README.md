# CodeGen - AI 代码生成平台

基于 Spring Boot + LangChain4j + Vue 3 的 AI 代码生成平台，支持通过自然语言描述生成前端代码。

## ✨ 功能特性

- 🤖 **AI 代码生成**：通过自然语言描述生成 HTML/CSS/JS 代码
- 📁 **多文件模式**：支持生成单文件 HTML 或多文件（HTML + CSS + JS）
- 🔄 **流式输出**：支持 SSE 流式返回生成结果
- 👤 **用户系统**：注册、登录、权限管理
- 📖 **接口文档**：集成 Knife4j/OpenAPI 接口文档

## 🛠️ 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.16 | 应用框架 |
| LangChain4j | 1.18.x | AI 集成框架 |
| MyBatis-Flex | 1.11.8 | ORM 框架 |
| MySQL | 8.x | 数据库 |
| Knife4j | 4.5.0 | 接口文档 |
| Java | 21 | 编程语言 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | 前端框架 |
| Vite | 7.0 | 构建工具 |
| Ant Design Vue | 4.x | UI 组件库 |
| TypeScript | 5.8 | 类型系统 |
| Pinia | 3.x | 状态管理 |

## 📁 项目结构

```
CodeGen/
├── src/main/java/com/renix/codegen/
│   ├── ai/                          # AI 相关
│   │   ├── model/                   # AI 模型（HtmlCodeResult, MultiFileCodeResult）
│   │   ├── AiCodeGeneratorService.java      # AI 服务接口
│   │   └── AiCodeGeneratorServiceFactory.java # 服务工厂
│   ├── controller/                  # 控制器
│   ├── core/                        # 核心模块
│   │   ├── parser/                  # 代码解析器（策略模式）
│   │   └── saver/                   # 文件保存器（模板方法模式）
│   ├── model/                       # 数据模型
│   │   ├── entity/                  # 实体类
│   │   ├── dto/                     # 数据传输对象
│   │   ├── vo/                      # 视图对象
│   │   └── enums/                   # 枚举类
│   ├── service/                     # 服务层
│   ├── mapper/                      # MyBatis Mapper
│   ├── config/                      # 配置类
│   ├── exception/                   # 异常处理
│   └── annotation/                  # 自定义注解
├── src/main/resources/
│   ├── application.yaml             # 主配置
│   ├── application-dev.yaml         # 开发环境配置
│   └── prompt/                      # AI 提示词模板
├── codegen-frontend/                # 前端项目
├── docker-compose.yaml              # Docker 配置
└── pom.xml                          # Maven 配置
```

## 🚀 快速开始

### 环境要求

- Java 21+
- Node.js 18+
- MySQL 8.x
- Maven 3.8+

### 后端启动

1. **启动 MySQL**

```bash
docker-compose up -d mysql
```

2. **修改配置**

编辑 `src/main/resources/application-dev.yaml`，配置数据库连接和 AI API Key。

3. **启动后端**

```bash
mvn spring-boot:run
```

后端启动在 `http://localhost:8123`

### 前端启动

```bash
cd codegen-frontend
npm install
npm run dev
```

前端启动在 `http://localhost:5173`

### 接口文档

访问 `http://localhost:8123/api/doc.html` 查看 Knife4j 接口文档。

---

## 🎨 设计模式

本项目在核心模块中使用了多种设计模式，提高代码的可维护性和扩展性。

### 1. 策略模式（Strategy Pattern）— `core/parser` 包

**场景**：不同类型代码的解析逻辑完全不同。

```
CodeParser<T> (策略接口)
    │
    ├── HtmlCodeParser (HTML 解析策略)
    │
    └── MultiFileCodeParser (多文件解析策略)
            │
            └── CodeParserExecutor (执行器 - 简单工厂)
```

**代码示例**：

```java
// 策略接口
public interface CodeParser<T> {
    T parseCode(String codeContent);
}

// HTML 解析策略
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {
    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        // 只提取 HTML 代码块
        String htmlCode = extractHtmlCode(codeContent);
        return new HtmlCodeResult(htmlCode);
    }
}

// 多文件解析策略
public class MultiFileCodeParser implements CodeParser<MultiFileCodeResult> {
    @Override
    public MultiFileCodeResult parseCode(String codeContent) {
        // 提取 HTML + CSS + JS 三个代码块
        String htmlCode = extractCodeByPattern(content, HTML_CODE_PATTERN);
        String cssCode = extractCodeByPattern(content, CSS_CODE_PATTERN);
        String jsCode = extractCodeByPattern(content, JS_CODE_PATTERN);
        return new MultiFileCodeResult(htmlCode, cssCode, jsCode);
    }
}

// 简单工厂 - 根据类型选择策略
public class CodeParserExecutor {
    public static Object executeParser(String codeContent, CodeGenTypeEnum type) {
        return switch (type) {
            case HTML -> htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
        };
    }
}
```

**为什么用策略模式**：两个解析器的流程完全不同，没有共同的算法骨架可以抽取。

---

### 2. 模板方法模式（Template Method Pattern）— `core/saver` 包

**场景**：不同文件类型的保存流程相同，只是保存的文件不同。

```
CodeFileSaverTemplate<T> (抽象模板)
    │
    ├── HtmlCodeFileSaverTemplate (保存单个 HTML)
    │
    └── MultiFileCodeFileSaverTemplate (保存 HTML + CSS + JS)
            │
            └── CodeFileSaverExecutor (执行器 - 简单工厂)
```

**代码示例**：

```java
// 抽象模板 - 定义算法骨架
public abstract class CodeFileSaverTemplate<T> {
    
    // 模板方法：固定流程（final 不允许子类修改）
    public final File saveCode(T result) {
        validateInput(result);              // 1. 验证输入（共同）
        String baseDirPath = buildUniqueDir();  // 2. 构建目录（共同）
        saveFiles(result, baseDirPath);     // 3. 保存文件（差异！）
        return new File(baseDirPath);       // 4. 返回目录（共同）
    }
    
    // 抽象方法：子类必须实现
    protected abstract void saveFiles(T result, String baseDirPath);
}

// HTML 保存器：只保存 index.html
public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {
    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
    }
}

// 多文件保存器：保存 HTML + CSS + JS
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {
    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
    }
}
```

**为什么用模板方法模式**：两个保存器有共同的流程（验证→建目录→保存→返回），只是"保存文件"这一步不同。

---

### 3. 简单工厂模式（Simple Factory Pattern）— `CodeParserExecutor` 和 `CodeFileSaverExecutor`

**场景**：根据类型创建对应的解析器或保存器。

```java
// 解析器工厂
public class CodeParserExecutor {
    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();
    private static final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    public static Object executeParser(String codeContent, CodeGenTypeEnum type) {
        return switch (type) {
            case HTML -> htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
        };
    }
}

// 保存器工厂
public class CodeFileSaverExecutor {
    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaver = new HtmlCodeFileSaverTemplate();
    private static final MultiFileCodeFileSaverTemplate multiFileCodeFileSaver = new MultiFileCodeFileSaverTemplate();

    public static File executeSaver(Object codeResult, CodeGenTypeEnum type) {
        return switch (type) {
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) codeResult);
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) codeResult);
        };
    }
}
```

---

### 设计模式对比

| 包 | 使用模式 | 核心区别 |
|----|----------|----------|
| `core/parser` | **策略模式** | 解析流程完全不同，每个策略独立 |
| `core/saver` | **模板方法模式** | 保存流程相同，只是保存文件不同 |

**选择原则**：
- **有共同算法骨架** → 模板方法（父类定义流程，子类填充细节）
- **没有共同流程** → 策略模式（每个算法独立，可互换）

---

## 📄 API 接口

### 用户相关

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/user/register` | POST | 用户注册 |
| `/api/user/login` | POST | 用户登录 |
| `/api/user/get/login` | GET | 获取当前登录用户 |
| `/api/user/logout` | POST | 用户登出 |

### AI 代码生成

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/ai/code/gen/html` | POST | 生成 HTML 代码 |
| `/api/ai/code/gen/multi-file` | POST | 生成多文件代码 |
| `/api/ai/code/gen/html/stream` | GET | 流式生成 HTML |
| `/api/ai/code/gen/multi-file/stream` | GET | 流式生成多文件 |

---

## 🔧 配置说明

### AI 配置（application-dev.yaml）

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: https://api.deepseek.com  # AI API 地址
      api-key: your-api-key                 # API Key
      model-name: deepseek-v4-flash         # 模型名称
      max-tokens: 8192                      # 最大 token 数
    streaming-chat-model:                   # 流式模型配置
      base-url: https://api.deepseek.com
      api-key: your-api-key
      model-name: deepseek-v4-flash
```

---

## 📝 开发说明

### 添加新的代码生成类型

1. 在 `CodeGenTypeEnum` 中添加新枚举
2. 在 `ai/model` 中添加新的结果模型
3. 在 `core/parser` 中添加新的解析器（实现 `CodeParser` 接口）
4. 在 `core/saver` 中添加新的保存器（继承 `CodeFileSaverTemplate`）
5. 在 `AiCodeGeneratorService` 中添加新的生成方法
6. 在 `CodeParserExecutor` 和 `CodeFileSaverExecutor` 中注册新类型

---

## 📊 接口文档

启动后端后访问：`http://localhost:8123/api/doc.html`

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License
