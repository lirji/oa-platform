export interface ApiResult<T> { code: string | number; message?: string; data: T }
export interface MyPermissions { userId: string | null; username: string | null; permCodes: string[]; dataScope: string; scopePrefixes: string[] }
export interface Todo { id: number; taskId: string; title: string; summary?: string; applicantName?: string; state: string; createdAt: string; dueAt?: string }
export interface PunchResult { accepted: boolean; duplicate: boolean; message: string }
export interface PunchRow { punchType?: string; punchTime?: string; type?: string; time?: string; source?: string; [key: string]: unknown }
export interface DirectoryEntry { employeeId: number; name: string; orgName?: string; positionName?: string; mobile?: string; email?: string }
export interface Announcement { id: number; title: string; content: string; publisherName?: string; publishedAt: string; expireAt?: string; readByMe?: boolean; status: string }
