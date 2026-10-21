import { fetchAuthSession } from 'aws-amplify/auth';
import { config } from './config';

export interface Producto {
  id: number;
  nombre: string;
  marca: string;
  categoria: string;
  precio: number;
  stock: number;
}

export interface NuevoProducto {
  nombre: string;
  marca: string;
  categoria: string;
  precio: number;
  stock: number;
}

export interface QueueStatus {
  visibleMessages: number;
  inFlightMessages: number;
  delayedMessages: number;
  oldestMessageAgeSeconds: number;
  deadLetterMessages: number;
  redrivePolicy: string;
}

export interface MessagingResource {
  name: string;
  arn: string;
  url: string;
  dlqName: string;
  filterPolicy: string;
  status: QueueStatus;
}

export interface MessagingTopology {
  topicName: string;
  topicArn: string;
  queues: MessagingResource[];
}

export type ConsumerStates = Record<string, boolean>;

async function authHeaders(options: RequestInit): Promise<HeadersInit> {
  const session = await fetchAuthSession();
  const token = session.tokens?.accessToken?.toString();
  return {
    'Content-Type': 'application/json',
    ...options.headers,
    ...(token ? { Authorization: 'Bearer ' + token } : {}),
  };
}

export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  return fetch(`${config.apiUrl}${path}`, {
    ...options,
    headers: await authHeaders(options),
  });
}

export async function agregarProducto(datos: NuevoProducto): Promise<Producto> {
  const response = await apiFetch('/api/productos', {
    method: 'POST',
    body: JSON.stringify(datos),
  });
  if (!response.ok) {
    const cuerpo = await response.json().catch(() => null);
    throw new Error(cuerpo?.mensaje ?? `El backend respondio ${response.status} al agregar el producto`);
  }
  return response.json();
}

export async function obtenerCatalogo(): Promise<Producto[]> {
  const response = await apiFetch('/api/productos');
  if (!response.ok) {
    throw new Error(`El backend respondio ${response.status} al pedir el inventario`);
  }
  return response.json();
}

export async function eliminarProducto(id: number): Promise<void> {
  const response = await apiFetch(`/api/productos/${id}`, { method: 'DELETE' });
  if (!response.ok) {
    const cuerpo = await response.json().catch(() => null);
    throw new Error(cuerpo?.mensaje ?? `El backend respondio ${response.status} al eliminar el producto`);
  }
}

export async function actualizarProducto(id: number, datos: NuevoProducto): Promise<Producto> {
  const response = await apiFetch(`/api/productos/${id}`, {
    method: 'PUT',
    body: JSON.stringify(datos),
  });
  if (!response.ok) {
    const cuerpo = await response.json().catch(() => null);
    throw new Error(cuerpo?.mensaje ?? `El backend respondio ${response.status} al actualizar el producto`);
  }
  return response.json();
}

async function messagingFetch(path: string, options: RequestInit = {}): Promise<Response> {
  if (!config.messagingApiUrl) {
    throw new Error('Falta configurar VITE_MESSAGING_API_URL');
  }
  return fetch(`${config.messagingApiUrl}${path}`, {
    ...options,
    headers: await authHeaders(options),
  });
}

async function messagingJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await messagingFetch(path, options);
  if (!response.ok) {
    throw new Error(`Mensajería respondió ${response.status}`);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

export function obtenerTopologia(): Promise<MessagingTopology> {
  return messagingJson('/api/messaging/admin/topology');
}

export function obtenerConsumidores(): Promise<ConsumerStates> {
  return messagingJson('/api/messaging/admin/consumers');
}

export function obtenerPoliticaRetencion(): Promise<Record<string, string>> {
  return messagingJson('/api/messaging/admin/cloudwatch/retention-policy');
}

export function crearAlarmasDlq(): Promise<string[]> {
  return messagingJson('/api/messaging/admin/cloudwatch/dlq-alarms', { method: 'POST' });
}

export function controlarConsumidor(consumer: string, action: 'pause' | 'resume'): Promise<void> {
  return messagingJson(`/api/messaging/admin/consumers/${consumer}/${action}`, { method: 'POST' });
}

export function reprocesarDlq(queueKey: string): Promise<{ movedMessages: number }> {
  return messagingJson(`/api/messaging/admin/queues/${queueKey}/dlq/reprocess`, { method: 'POST' });
}
