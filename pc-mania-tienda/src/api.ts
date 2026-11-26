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

export interface CrearOrdenRequest {
  lineas: Array<{ productoId: number; cantidad: number }>;
}

export interface Orden {
  id: string;
  estado: string;
  creadaEn: string;
  lineas: Array<{
    productoId: number;
    nombreProducto: string;
    cantidad: number;
    precioUnitario: number;
    subtotal: number;
  }>;
  total: number;
}

export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const session = await fetchAuthSession();
  const token = session.tokens?.accessToken?.toString();

  return fetch(`${config.apiUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
}

export async function obtenerCatalogo(): Promise<Producto[]> {
  const response = await apiFetch('/api/productos');
  if (!response.ok) {
    throw new Error(`El backend respondio ${response.status} al pedir el catalogo`);
  }
  return response.json();
}

export async function crearOrden(request: CrearOrdenRequest): Promise<Orden> {
  const response = await apiFetch('/api/ordenes', {
    method: 'POST',
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { mensaje?: string } | null;
    throw new Error(body?.mensaje ?? `El backend respondió ${response.status} al crear la orden`);
  }
  return response.json();
}
