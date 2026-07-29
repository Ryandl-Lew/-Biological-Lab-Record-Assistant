/**
 * 通用类型定义（JSDoc）与基础常量
 * 贯穿各业务领域的基础类型说明。JS 中类型仅作文档与编辑器提示用途。
 *
 * @typedef {string} ID 后端实体主键
 * @typedef {string} ISODateTime ISO 日期时间字符串，如 "2026-07-07 16:00"
 *
 * @typedef {'blue'|'green'|'amber'|'red'|'violet'|'gray'} BadgeTone 徽章色系
 *
 * @typedef {Object} PageQuery
 * @property {number} [page]
 * @property {number} [pageSize]
 * @property {string} [keyword]
 *
 * @template T
 * @typedef {Object} Paginated
 * @property {T[]} list
 * @property {number} total
 * @property {number} page
 * @property {number} pageSize
 */

export {}
