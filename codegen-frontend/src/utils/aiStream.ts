export interface StreamMessagePayload {
  type?: 'ai_response' | 'tool_request' | 'tool_executed'
  content?: string
  d?: string
  id?: string
  index?: number
  name?: string
  arguments?: string
  result?: string
}

interface TextOutputSegment {
  kind: 'text'
  content: string
}

interface ToolOutputSegment {
  kind: 'tool'
  key: string
  name: string
  arguments: string
  result?: string
  status: 'streaming' | 'executed'
}

type OutputSegment = TextOutputSegment | ToolOutputSegment

const STREAM_MESSAGE_TYPES = new Set(['ai_response', 'tool_request', 'tool_executed'])

const isRecord = (value: unknown): value is Record<string, unknown> => {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * 同时兼容直接传输 { type, ... } 的新协议与 { d: "{ type, ... }" } 旧协议。
 */
export const parseStreamMessage = (eventData: string): StreamMessagePayload => {
  let value: unknown = JSON.parse(eventData)

  if (isRecord(value) && typeof value.d === 'string') {
    const legacyContent = value.d
    try {
      const nestedValue: unknown = JSON.parse(legacyContent)
      if (isRecord(nestedValue) && typeof nestedValue.type === 'string') {
        value = nestedValue
      } else {
        return { type: 'ai_response', content: legacyContent }
      }
    } catch {
      return { type: 'ai_response', content: legacyContent }
    }
  }

  if (typeof value === 'string') {
    return { type: 'ai_response', content: value }
  }
  if (!isRecord(value)) {
    throw new Error('SSE 消息格式无效')
  }

  return value as StreamMessagePayload
}

const getToolDisplayName = (toolName?: string) => {
  if (toolName === 'writeFile') return '写入文件'
  return toolName || '未知工具'
}

const getCodeLanguage = (fileName?: string) => {
  const extension = fileName?.split('.').pop()?.toLowerCase()
  const languageMap: Record<string, string> = {
    js: 'javascript',
    mjs: 'javascript',
    cjs: 'javascript',
    ts: 'typescript',
    jsx: 'javascript',
    tsx: 'typescript',
    vue: 'vue',
    html: 'html',
    css: 'css',
    scss: 'scss',
    json: 'json',
    md: 'markdown',
  }
  return (extension && languageMap[extension]) || extension || 'text'
}

const createCodeFence = (content: string) => {
  const runs = content.match(/`+/g) || []
  const longestRun = runs.reduce((max, run) => Math.max(max, run.length), 0)
  return '`'.repeat(Math.max(3, longestRun + 1))
}

const formatToolOutput = (segment: ToolOutputSegment) => {
  const toolName = getToolDisplayName(segment.name)
  const selection = `\n\n[选择工具] ${toolName}\n\n`

  if (segment.status === 'streaming') {
    if (!segment.arguments) return selection
    const fence = createCodeFence(segment.arguments)
    return (
      selection +
      `[工具调用中] ${toolName}\n\n${fence}json\n${segment.arguments}\n${fence}\n\n`
    )
  }

  try {
    const args = JSON.parse(segment.arguments || '{}') as Record<string, unknown>
    const fileName = typeof args.relativeFilePath === 'string' ? args.relativeFilePath : ''
    const fileContent = typeof args.content === 'string' ? args.content : segment.arguments
    const fence = createCodeFence(fileContent)
    const result = segment.result ? `> ${segment.result}\n\n` : ''
    const fileLabel = fileName ? ` \`${fileName.replace(/`/g, '\\`')}\`` : ''

    return (
      selection +
      `[工具调用] ${toolName}${fileLabel}\n\n` +
      `${fence}${getCodeLanguage(fileName)}\n${fileContent}\n${fence}\n\n${result}`
    )
  } catch {
    const fence = createCodeFence(segment.arguments)
    const result = segment.result ? `> ${segment.result}\n\n` : ''
    return (
      selection +
      `[工具调用] ${toolName}\n\n${fence}json\n${segment.arguments}\n${fence}\n\n${result}`
    )
  }
}

/** 将 AI 文本、工具参数片段和工具结果合并为一段持续更新的 Markdown。 */
export class AiStreamAccumulator {
  private readonly segments: OutputSegment[] = []
  private readonly tools = new Map<string, ToolOutputSegment>()
  private sequence = 0

  consume(payload: StreamMessagePayload) {
    if (payload.type === 'ai_response') {
      this.appendText(payload.content || '')
      return
    }

    if (payload.type === 'tool_request') {
      let tool = this.findTool(payload)
      if (!tool) {
        tool = this.createTool(payload, 'streaming')
      }
      tool.arguments += payload.arguments || ''
      return
    }

    if (payload.type === 'tool_executed') {
      let tool = this.findTool(payload)
      if (!tool) {
        tool = this.createTool(payload, 'streaming')
      }
      tool.name = payload.name || tool.name
      // 完成事件携带完整参数，用它替换此前拼接的 partialArguments。
      tool.arguments = payload.arguments || tool.arguments
      tool.result = payload.result
      tool.status = 'executed'
    }
  }

  toMarkdown() {
    return this.segments
      .map((segment) => (segment.kind === 'text' ? segment.content : formatToolOutput(segment)))
      .join('')
  }

  private appendText(content: string) {
    const lastSegment = this.segments[this.segments.length - 1]
    if (lastSegment?.kind === 'text') {
      lastSegment.content += content
    } else {
      this.segments.push({ kind: 'text', content })
    }
  }

  private findTool(payload: StreamMessagePayload) {
    if (payload.id && this.tools.has(payload.id)) {
      return this.tools.get(payload.id)
    }
    const indexedKey = payload.index === undefined ? undefined : `${payload.name}:${payload.index}`
    if (indexedKey && this.tools.has(indexedKey)) {
      return this.tools.get(indexedKey)
    }
    return [...new Set(this.tools.values())]
      .reverse()
      .find((tool) => tool.status === 'streaming' && tool.name === payload.name)
  }

  private createTool(payload: StreamMessagePayload, status: ToolOutputSegment['status']) {
    const indexedKey =
      payload.index === undefined ? undefined : `${payload.name}:${payload.index}`
    const key = payload.id || indexedKey || `${payload.name}:tool:${this.sequence++}`
    const tool: ToolOutputSegment = {
      kind: 'tool',
      key,
      name: payload.name || '',
      arguments: '',
      status,
    }
    this.segments.push(tool)
    this.tools.set(key, tool)
    if (payload.id) this.tools.set(payload.id, tool)
    if (indexedKey) this.tools.set(indexedKey, tool)
    return tool
  }
}

/** 恢复旧版本以连续 JSON 事件形式保存的 AI 历史消息。 */
export const normalizeStoredAiMessage = (content: string) => {
  const events: StreamMessagePayload[] = []
  let cursor = 0

  while (cursor < content.length) {
    while (cursor < content.length && /\s/.test(content[cursor])) cursor++
    if (cursor >= content.length) break
    if (content[cursor] !== '{') return content

    const start = cursor
    let depth = 0
    let inString = false
    let escaped = false
    let foundBoundary = false

    for (; cursor < content.length; cursor++) {
      const char = content[cursor]
      if (inString) {
        if (escaped) {
          escaped = false
        } else if (char === '\\') {
          escaped = true
        } else if (char === '"') {
          inString = false
        }
        continue
      }

      if (char === '"') {
        inString = true
      } else if (char === '{') {
        depth++
      } else if (char === '}') {
        depth--
        if (depth === 0) {
          cursor++
          foundBoundary = true
          break
        }
      }
    }

    if (!foundBoundary) return content
    try {
      const event = parseStreamMessage(content.slice(start, cursor))
      if (!event.type || !STREAM_MESSAGE_TYPES.has(event.type)) return content
      events.push(event)
    } catch {
      return content
    }
  }

  if (events.length === 0) return content
  const accumulator = new AiStreamAccumulator()
  events.forEach((event) => accumulator.consume(event))
  return accumulator.toMarkdown()
}
