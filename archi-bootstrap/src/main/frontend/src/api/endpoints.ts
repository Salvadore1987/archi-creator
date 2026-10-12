import { request } from './http';
import type {
  Bendpoint,
  Element,
  ElementType,
  Finding,
  Folder,
  LockInfo,
  Me,
  ModelSummary,
  ModelTree,
  Property,
  Relationship,
  RelationType,
  SaveResult,
  UiConfig,
  Uuid,
  VersionInfo,
  ViewEdge,
  ViewNode,
  ViewPayload,
  ViewSummary,
} from './types';

export const api = {
  uiConfig: () => request<UiConfig>('/ui-config'),
  me: () => request<Me>('/me'),

  listModels: () => request<ModelSummary[]>('/models'),
  createModel: (name: string, key: string) =>
    request<ModelSummary>('/models', { method: 'POST', body: { name }, idempotencyKey: key }),
  importModel: (file: File, key: string) => {
    const form = new FormData();
    form.append('file', file);
    return request<{ modelId?: Uuid; status: string }>('/models/import', {
      method: 'POST',
      body: form,
      idempotencyKey: key,
    });
  },
  exportModel: (modelId: Uuid) => request<Response>(`/models/${modelId}/export`, { raw: true }),

  openModel: (modelId: Uuid) => request<ModelTree>(`/models/${modelId}`),
  openView: (viewId: Uuid) => request<ViewPayload>(`/views/${viewId}`),
  validate: (modelId: Uuid) => request<Finding[]>(`/models/${modelId}/validate`),
  versions: (modelId: Uuid) => request<VersionInfo[]>(`/models/${modelId}/versions`),

  currentLock: (modelId: Uuid) => request<LockInfo | undefined>(`/models/${modelId}/lock`),
  acquireLock: (modelId: Uuid) => request<LockInfo>(`/models/${modelId}/lock`, { method: 'POST' }),
  releaseLock: (modelId: Uuid, keepalive = false) =>
    request<void>(`/models/${modelId}/lock`, { method: 'DELETE', keepalive }),
  saveVersion: (modelId: Uuid, key: string, comment?: string) =>
    request<SaveResult>(`/models/${modelId}/versions`, { method: 'POST', body: { comment }, idempotencyKey: key }),

  elementTypes: () => request<ElementType[]>('/metamodel/elements'),
  relationTypes: (source: string, target: string) =>
    request<RelationType[]>(
      `/metamodel/relations?source=${encodeURIComponent(source)}&target=${encodeURIComponent(target)}`,
    ),

  // ── Команды синхронизации сессии правки ─────────────────────────
  createFolder: (modelId: Uuid, body: { id: Uuid; archiId: string; parentId: Uuid; name: string }) =>
    request<Folder>(`/models/${modelId}/folders`, { method: 'POST', body }),
  createElement: (
    modelId: Uuid,
    body: { id: Uuid; archiId: string; archiType: string; name: string; folderId: Uuid },
  ) => request<Element>(`/models/${modelId}/elements`, { method: 'POST', body }),
  updateElement: (id: Uuid, body: { name?: string; documentation?: string; properties?: Property[] }) =>
    request<Element>(`/elements/${id}`, { method: 'PATCH', body }),
  deleteElement: (id: Uuid) => request<void>(`/elements/${id}`, { method: 'DELETE' }),
  createRelationship: (
    modelId: Uuid,
    body: {
      id: Uuid;
      archiId: string;
      archiType: string;
      sourceId: Uuid;
      targetId: Uuid;
      name?: string;
      folderId: Uuid;
      accessType?: string;
      directed?: boolean;
    },
  ) => request<Relationship>(`/models/${modelId}/relationships`, { method: 'POST', body }),
  updateRelationship: (id: Uuid, body: { name?: string; documentation?: string; properties?: Property[] }) =>
    request<Relationship>(`/relationships/${id}`, { method: 'PATCH', body }),
  deleteRelationship: (id: Uuid) => request<void>(`/relationships/${id}`, { method: 'DELETE' }),
  renameItem: (modelId: Uuid, itemId: Uuid, name: string) =>
    request<void>(`/models/${modelId}/tree/${itemId}`, { method: 'PATCH', body: { name } }),
  moveItems: (modelId: Uuid, targetFolderId: Uuid, itemIds: Uuid[]) =>
    request<void>(`/models/${modelId}/tree/move`, { method: 'POST', body: { targetFolderId, itemIds } }),
  deleteItems: (modelId: Uuid, itemIds: Uuid[]) =>
    request<void>(`/models/${modelId}/tree/delete`, { method: 'POST', body: { itemIds } }),
  createView: (modelId: Uuid, body: { id: Uuid; archiId: string; name: string; folderId: Uuid }) =>
    request<ViewSummary>(`/models/${modelId}/views`, { method: 'POST', body }),
  placeNode: (
    viewId: Uuid,
    body: {
      id: Uuid;
      archiId: string;
      elementId: Uuid;
      x: number;
      y: number;
      width: number;
      height: number;
      parentId?: Uuid;
    },
  ) => request<ViewNode>(`/views/${viewId}/nodes`, { method: 'POST', body }),
  removeNode: (nodeId: Uuid) => request<void>(`/view-nodes/${nodeId}`, { method: 'DELETE' }),
  placeEdge: (
    viewId: Uuid,
    body: {
      id: Uuid;
      archiId: string;
      relationshipId: Uuid;
      sourceId: Uuid;
      targetId: Uuid;
      bendpoints: Bendpoint[];
    },
  ) => request<ViewEdge>(`/views/${viewId}/edges`, { method: 'POST', body }),
  saveLayout: (
    viewId: Uuid,
    body: {
      nodes: Array<{ id: Uuid; x: number; y: number; width: number; height: number }>;
      edges: Array<{ id: Uuid; bendpoints: Bendpoint[] }>;
    },
  ) => request<void>(`/views/${viewId}/layout`, { method: 'PUT', body }),
};
