/**
 * Формы ответов и запросов REST по контракту сервера. Отсутствующее значение
 * сервер не пишет вовсе (`non_null`), поэтому необязательные поля — `?`.
 */

export type Uuid = string;

export interface ModelSummary {
  id: Uuid;
  workspaceId: Uuid;
  archiId: string;
  name: string;
  documentation?: string;
  status: 'ACTIVE' | 'DELETED';
  archiVersion?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Property {
  key: string;
  value: string;
}

export type FolderType =
  | 'STRATEGY'
  | 'BUSINESS'
  | 'APPLICATION'
  | 'TECHNOLOGY'
  | 'MOTIVATION'
  | 'IMPLEMENTATION_MIGRATION'
  | 'OTHER'
  | 'RELATIONS'
  | 'DIAGRAMS';

export interface Folder {
  id: Uuid;
  parentId?: Uuid;
  archiId: string;
  name: string;
  folderType?: FolderType;
  sortOrder: number;
}

export interface Element {
  id: Uuid;
  folderId: Uuid;
  archiId: string;
  archiType: string;
  layer: string;
  name: string;
  documentation?: string;
  properties: Property[];
  supported: boolean;
  sortOrder: number;
}

export type AccessType = 'WRITE' | 'READ' | 'ACCESS' | 'READ_WRITE';

export interface Relationship {
  id: Uuid;
  folderId: Uuid;
  archiId: string;
  archiType: string;
  sourceId: Uuid;
  targetId: Uuid;
  name?: string;
  documentation?: string;
  accessType?: AccessType;
  directed?: boolean;
  properties: Property[];
  supported: boolean;
  sortOrder: number;
  edgeId?: Uuid;
}

export interface ViewSummary {
  id: Uuid;
  folderId: Uuid;
  archiId: string;
  archiType: string;
  name: string;
  sortOrder: number;
}

export interface Placement {
  viewId: Uuid;
  elementIds: Uuid[];
  relationshipIds: Uuid[];
}

export interface ModelTree {
  model: ModelSummary;
  folders: Folder[];
  elements: Element[];
  relationships: Relationship[];
  views: ViewSummary[];
  placements?: Placement[];
}

export interface Style {
  fillColor?: string;
  font?: string;
  fontColor?: string;
  lineColor?: string;
  textAlignment?: number;
}

export type NodeKind = 'DIAGRAM_OBJECT' | 'GROUP' | 'NOTE' | 'OTHER';

export interface ViewNode {
  id: Uuid;
  parentId?: Uuid;
  archiId: string;
  archiType: string;
  kind: NodeKind;
  elementId?: Uuid;
  x: number;
  y: number;
  width: number;
  height: number;
  style?: Style;
  label?: string;
  content?: string;
  sortOrder: number;
}

export interface Bendpoint {
  startX: number;
  startY: number;
  endX: number;
  endY: number;
}

export interface ViewEdge {
  id: Uuid;
  archiId: string;
  archiType: string;
  relationshipId?: Uuid;
  sourceId: Uuid;
  targetId: Uuid;
  bendpoints: Bendpoint[];
  style?: Style;
  sortOrder: number;
}

export interface ViewPayload {
  id: Uuid;
  modelId: Uuid;
  folderId: Uuid;
  archiId: string;
  archiType: string;
  name: string;
  documentation?: string;
  viewpoint?: string;
  properties: Property[];
  editable: boolean;
  nodes: ViewNode[];
  edges: ViewEdge[];
}

export interface VersionInfo {
  versionNo: number;
  author: string;
  comment?: string;
  label?: string;
  createdAt: string;
  snapshotAvailable: boolean;
  gitSha?: string;
}

export interface SaveResult {
  version: VersionInfo;
  created: boolean;
}

export interface LockInfo {
  modelId: Uuid;
  owner: string;
  acquiredAt: string;
  expiresAt: string;
}

export interface Finding {
  severity: 'ERROR' | 'WARNING' | 'INFO';
  code: string;
  message: string;
  targetKind?: string;
  targetId?: string;
  suggestion?: string;
}

export interface ElementType {
  archiType: string;
  kind: 'ELEMENT' | 'JUNCTION';
  layer: string;
  phase?: string;
  supported: boolean;
}

export interface RelationType {
  archiType: string;
  type: string;
  byDefault: boolean;
}

export type Role = 'VIEWER' | 'ARCHITECT' | 'ADMIN';

export interface Me {
  subject: string;
  displayName: string;
  roles: Role[];
}

export interface UiConfig {
  oidc?: { authority: string; clientId: string };
}

/** Тело ответа об ошибке: RFC 7807 плюс код инварианта и данные по ситуации. */
export interface Problem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  lockOwner?: string;
  expiresAt?: string;
  relationships?: Uuid[];
  permitted?: string[];
}
