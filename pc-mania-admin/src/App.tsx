import { useEffect, useRef, useState, type FormEvent } from 'react';
import { createPortal } from 'react-dom';
import { fetchAuthSession, signInWithRedirect, signOut } from 'aws-amplify/auth';
import {
  actualizarProducto,
  agregarProducto,
  controlarConsumidor,
  crearAlarmasDlq,
  crearTopologiaKinesis,
  crearAlarmasIteratorAge,
  eliminarProducto,
  eliminarStreamKinesis,
  obtenerCatalogo,
  obtenerConsumidores,
  obtenerConsumidoresKinesis,
  obtenerPoliticaRetencion,
  obtenerStreamsKinesis,
  obtenerTopologia,
  reprocesarDlq,
  actualizarRetencionKinesis,
  actualizarShardsKinesis,
  publicarEventoKinesis,
  obtenerEstadoEventoKinesis,
  obtenerEstadosEventosKinesis,
  type ConsumerStates,
  type KinesisConsumerStatus,
  type KinesisStreamStatus,
  type KinesisEventStatus,
  type MessagingTopology,
  type NuevoProducto,
  type Producto,
} from './api';
import { configFaltante, isConfigOk } from './config';
import './App.css';

type ThemeMode = 'light' | 'dark';
const THEME_KEY = 'pcmania-admin-theme';

const FORM_INICIAL = { nombre: '', marca: '', categoria: '', precio: '', stock: '' };
type OrdenProductos = 'nombre' | 'precio-asc' | 'precio-desc' | 'stock-desc';

function getStoredTheme(): ThemeMode {
  if (typeof window === 'undefined') return 'dark';
  const stored = window.localStorage.getItem(THEME_KEY);
  return stored === 'light' || stored === 'dark' ? stored : 'dark';
}

const matrixCharacters = 'アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポ';

function MatrixBackground() {
  return (
    <div className="jp-matrix" aria-hidden="true">
      {Array.from({ length: 1200 }, (_, index) => (
        <span key={index}>{matrixCharacters[index % matrixCharacters.length]}</span>
      ))}
    </div>
  );
}

type FiltroSelectProps = {
  value: string;
  placeholder: string;
  options: string[];
  ariaLabel: string;
  onChange: (value: string) => void;
};

function etiquetaFiltro(valor: string) {
  return {
    nombre: 'Nombre',
    'precio-asc': 'Precio: menor a mayor',
    'precio-desc': 'Precio: mayor a menor',
    'stock-desc': 'Mayor stock',
  }[valor] ?? valor;
}

function FiltroSelect({ value, placeholder, options, ariaLabel, onChange }: FiltroSelectProps) {
  const [abierto, setAbierto] = useState(false);
  const contenedorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function cerrarAlHacerClickFuera(event: MouseEvent) {
      if (contenedorRef.current && !contenedorRef.current.contains(event.target as Node)) {
        setAbierto(false);
      }
    }

    document.addEventListener('mousedown', cerrarAlHacerClickFuera);
    return () => document.removeEventListener('mousedown', cerrarAlHacerClickFuera);
  }, []);

  return (
    <div className={`filtro-select ${abierto ? 'abierto' : ''}`} ref={contenedorRef}>
      <button
        type="button"
        className="filtro-select-trigger"
        aria-label={ariaLabel}
        aria-expanded={abierto}
        aria-haspopup="listbox"
        onClick={() => setAbierto((actual) => !actual)}
      >
        {value ? etiquetaFiltro(value) : placeholder}
      </button>
      {abierto && (
        <div className="filtro-select-menu" role="listbox" aria-label={ariaLabel}>
          <button type="button" role="option" aria-selected={!value} className={!value ? 'seleccionado' : ''} onClick={() => { onChange(''); setAbierto(false); }}>
            {placeholder}
          </button>
          {options.map((option) => (
            <button key={option} type="button" role="option" aria-selected={value === option} className={value === option ? 'seleccionado' : ''} onClick={() => { onChange(option); setAbierto(false); }}>
              {etiquetaFiltro(option)}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function App() {
  const [logueado, setLogueado] = useState(false);
  const [cargandoSesion, setCargandoSesion] = useState(isConfigOk);
  const [form, setForm] = useState(FORM_INICIAL);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mensajeExito, setMensajeExito] = useState<string | null>(null);
  const [agregados, setAgregados] = useState<Producto[]>([]);
  const [catalogo, setCatalogo] = useState<Producto[]>([]);
  const [cargandoCatalogo, setCargandoCatalogo] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const [categoriaSeleccionada, setCategoriaSeleccionada] = useState('');
  const [marcaSeleccionada, setMarcaSeleccionada] = useState('');
  const [eliminandoId, setEliminandoId] = useState<number | null>(null);
  const [productoParaEliminar, setProductoParaEliminar] = useState<Producto | null>(null);
  const [editandoId, setEditandoId] = useState<number | null>(null);
  const [orden, setOrden] = useState<OrdenProductos>('nombre');
  const [pagina, setPagina] = useState(1);
  const [theme, setTheme] = useState<ThemeMode>(getStoredTheme);
  const [settingsState, setSettingsState] = useState<'closed' | 'open' | 'closing'>('closed');
  const [mensajeria, setMensajeria] = useState<MessagingTopology | null>(null);
  const [consumidores, setConsumidores] = useState<ConsumerStates>({});
  const [politicaRetencion, setPoliticaRetencion] = useState<Record<string, string>>({});
  const [cargandoMensajeria, setCargandoMensajeria] = useState(false);
  const [mensajeMensajeria, setMensajeMensajeria] = useState<string | null>(null);
  const [streamsKinesis, setStreamsKinesis] = useState<KinesisStreamStatus[]>([]);
  const [consumidoresKinesis, setConsumidoresKinesis] = useState<KinesisConsumerStatus[]>([]);
  const [tipoEventoKinesis, setTipoEventoKinesis] = useState<'product.created' | 'product.updated' | 'order.created'>('product.created');
  const [falloKinesis, setFalloKinesis] = useState(false);
  const [publicandoKinesis, setPublicandoKinesis] = useState(false);
  const [resultadoKinesis, setResultadoKinesis] = useState<string | null>(null);
  const [errorKinesis, setErrorKinesis] = useState<string | null>(null);
  const [cerrandoResultadoKinesis, setCerrandoResultadoKinesis] = useState(false);
  const [ultimoEventIdKinesis, setUltimoEventIdKinesis] = useState<string | null>(null);
  const [repetirEventIdKinesis, setRepetirEventIdKinesis] = useState(false);
  const [estadosEventosKinesis, setEstadosEventosKinesis] = useState<KinesisEventStatus[]>([]);
  const [tipoResultadoKinesis, setTipoResultadoKinesis] = useState<'processed' | 'duplicate' | 'dlq' | 'pending' | null>(null);

  useEffect(() => {
    document.body.dataset.theme = theme;
    window.localStorage.setItem(THEME_KEY, theme);
  }, [theme]);

  useEffect(() => {
    if (settingsState !== 'closing') return;

    const timer = window.setTimeout(() => {
      setSettingsState('closed');
    }, 220);

    return () => window.clearTimeout(timer);
  }, [settingsState]);

  useEffect(() => {
    if (!resultadoKinesis && !errorKinesis) return;

    setCerrandoResultadoKinesis(false);
    const closingTimer = window.setTimeout(() => {
      setCerrandoResultadoKinesis(true);
    }, 4700);
    const removeTimer = window.setTimeout(() => {
      setResultadoKinesis(null);
      setErrorKinesis(null);
      setTipoResultadoKinesis(null);
      setCerrandoResultadoKinesis(false);
    }, 5000);

    return () => {
      window.clearTimeout(closingTimer);
      window.clearTimeout(removeTimer);
    };
  }, [resultadoKinesis, errorKinesis]);

  useEffect(() => {
    if (!isConfigOk) return;

    fetchAuthSession()
      .then((session) => setLogueado(!!session.tokens))
      .catch(() => setLogueado(false))
      .finally(() => setCargandoSesion(false));
  }, []);

  async function cargarCatalogo() {
    setCargandoCatalogo(true);
    try {
      setCatalogo(await obtenerCatalogo());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error desconocido');
    } finally {
      setCargandoCatalogo(false);
    }
  }

  async function cargarMensajeria() {
    setCargandoMensajeria(true);
    setMensajeMensajeria(null);
    try {
      const [topologia, estados, politica] = await Promise.all([
        obtenerTopologia(),
        obtenerConsumidores(),
        obtenerPoliticaRetencion(),
      ]);
      setMensajeria(topologia);
      setConsumidores(estados);
      setPoliticaRetencion(politica);
      try {
        const [streams, consumers, eventStates] = await Promise.all([
          obtenerStreamsKinesis(),
          obtenerConsumidoresKinesis(),
          obtenerEstadosEventosKinesis(),
        ]);
        setStreamsKinesis(streams);
        setConsumidoresKinesis(consumers);
        setEstadosEventosKinesis(eventStates);
      } catch {
        setStreamsKinesis([]);
        setConsumidoresKinesis([]);
      }
    } catch (err) {
      setMensajeMensajeria(err instanceof Error ? err.message : 'No se pudo cargar la mensajería');
    } finally {
      setCargandoMensajeria(false);
    }
  }

  async function ejecutarAccionMensajeria(action: () => Promise<unknown>, mensaje: string) {
    try {
      await action();
      setMensajeMensajeria(mensaje);
      await cargarMensajeria();
    } catch (err) {
      setMensajeMensajeria(err instanceof Error ? err.message : 'No se pudo completar la operación');
    }
  }

  async function publicarEventoDemo() {
    setPublicandoKinesis(true);
    setResultadoKinesis(null);
    setErrorKinesis(null);
    setTipoResultadoKinesis(null);
    try {
      const eventIdOverride = repetirEventIdKinesis ? ultimoEventIdKinesis ?? undefined : undefined;
      const result = await publicarEventoKinesis(tipoEventoKinesis, falloKinesis, eventIdOverride);
      setUltimoEventIdKinesis(result.eventId);

      let consumerStatus: KinesisEventStatus | null = null;
      for (let attempt = 0; attempt < 10 && !consumerStatus; attempt++) {
        await new Promise((resolve) => window.setTimeout(resolve, 400));
        consumerStatus = await obtenerEstadoEventoKinesis(result.eventId);
      }
      if (consumerStatus) {
        setEstadosEventosKinesis((previous) => [
          consumerStatus!,
          ...previous.filter((item) =>
            !(item.eventId === consumerStatus!.eventId
              && item.applicationName === consumerStatus!.applicationName)),
        ].slice(0, 20));
        if (consumerStatus.status === 'DUPLICATE') {
          setTipoResultadoKinesis('duplicate');
          setResultadoKinesis(
            `DUPLICATE - Duplicado detectado · eventId ${result.eventId} · consumidor ${consumerStatus.applicationName}`,
          );
        } else if (consumerStatus.status === 'DLQ') {
          setTipoResultadoKinesis('dlq');
          setResultadoKinesis(
            `DLQ - Evento enviado al DLQ · eventId ${result.eventId} · consumidor ${consumerStatus.applicationName}`,
          );
        } else if (repetirEventIdKinesis) {
          setTipoResultadoKinesis('pending');
          setResultadoKinesis(
            `DUPLICATE PENDING - Aún no se confirmó el duplicado · eventId ${result.eventId}`,
          );
        } else {
          setTipoResultadoKinesis('processed');
          setResultadoKinesis(
            `PROCESSED - Evento procesado correctamente · eventId ${result.eventId}`,
          );
        }
      }

      const streams = await obtenerStreamsKinesis();
      setStreamsKinesis(streams);
    } catch (err) {
      setErrorKinesis(err instanceof Error ? err.message : 'No se pudo publicar el evento Kinesis');
    } finally {
      setPublicandoKinesis(false);
    }
  }

  useEffect(() => {
    if (!logueado) return;

    void cargarCatalogo();
    void cargarMensajeria();
  }, [logueado]);

  useEffect(() => {
    setPagina(1);
  }, [busqueda, categoriaSeleccionada, marcaSeleccionada, orden]);

  if (!isConfigOk) {
    return (
      <div className="aviso-config">
        <p className="eyebrow">PC MANIA / CONFIGURACION</p>
        <h1>Conecta tu panel</h1>
        <p>Completa estos valores en el archivo <code>.env</code> cuando configures Cognito:</p>
        <ul>{configFaltante().map((clave) => <li key={clave}><code>{clave}</code></li>)}</ul>
      </div>
    );
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setMensajeExito(null);
    setEnviando(true);
    const datos: NuevoProducto = {
      nombre: form.nombre,
      marca: form.marca,
      categoria: form.categoria,
      precio: Number(form.precio),
      stock: Number(form.stock),
    };
    const normalizar = (valor: string) => valor.trim().toLowerCase();
    const duplicado = catalogo.some((producto) => producto.id !== editandoId
      && normalizar(producto.nombre) === normalizar(datos.nombre)
      && normalizar(producto.marca) === normalizar(datos.marca)
      && normalizar(producto.categoria) === normalizar(datos.categoria));

    if (duplicado) {
      setError('Ya existe un producto con el mismo nombre, marca y categoría.');
      setEnviando(false);
      return;
    }

    try {
      if (editandoId !== null) {
        const actualizado = await actualizarProducto(editandoId, datos);
        setCatalogo((prev) => prev.map((producto) => producto.id === editandoId ? actualizado : producto));
        setAgregados((prev) => prev.map((producto) => producto.id === editandoId ? actualizado : producto));
        setMensajeExito('Producto actualizado correctamente.');
      } else {
        const nuevo = await agregarProducto(datos);
        setAgregados((prev) => [nuevo, ...prev]);
        setCatalogo((prev) => [nuevo, ...prev]);
        setMensajeExito('Producto agregado correctamente.');
      }
      setForm(FORM_INICIAL);
      setEditandoId(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error desconocido');
    } finally {
      setEnviando(false);
    }
  }

  async function onEliminarConfirmado() {
    if (!productoParaEliminar) return;

    const producto = productoParaEliminar;
    setProductoParaEliminar(null);
    setError(null);
    setMensajeExito(null);
    setEliminandoId(producto.id);
    try {
      await eliminarProducto(producto.id);
      setCatalogo((prev) => prev.filter((actual) => actual.id !== producto.id));
      setAgregados((prev) => prev.filter((actual) => actual.id !== producto.id));
      setMensajeExito('Producto eliminado correctamente.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error desconocido');
    } finally {
      setEliminandoId(null);
    }
  }

  function iniciarEdicion(producto: Producto) {
    setEditandoId(producto.id);
    setForm({
      nombre: producto.nombre,
      marca: producto.marca,
      categoria: producto.categoria,
      precio: String(producto.precio),
      stock: String(producto.stock),
    });
    setError(null);
    setMensajeExito(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  const categorias = [...new Set(catalogo.map((producto) => producto.categoria))].sort();
  const marcas = [...new Set(catalogo.map((producto) => producto.marca))].sort();
  const catalogoFiltrado = catalogo.filter((producto) => {
    const texto = busqueda.trim().toLowerCase();
    const coincideBusqueda = !texto || [producto.nombre, producto.marca, producto.categoria]
      .some((valor) => valor.toLowerCase().includes(texto));
    const coincideCategoria = !categoriaSeleccionada || producto.categoria === categoriaSeleccionada;
    const coincideMarca = !marcaSeleccionada || producto.marca === marcaSeleccionada;
    return coincideBusqueda && coincideCategoria && coincideMarca;
  }).sort((a, b) => {
    if (orden === 'nombre') return a.nombre.localeCompare(b.nombre);
    if (orden === 'precio-asc') return a.precio - b.precio;
    if (orden === 'precio-desc') return b.precio - a.precio;
    return b.stock - a.stock;
  });
  const productosPorPagina = 6;
  const totalPaginas = Math.max(1, Math.ceil(catalogoFiltrado.length / productosPorPagina));
  const paginaActual = Math.min(pagina, totalPaginas);
  const productosVisibles = catalogoFiltrado.slice((paginaActual - 1) * productosPorPagina, paginaActual * productosPorPagina);

  return (
    <>
    <div className="app-shell">
      <MatrixBackground />
      <div className="app">
        <header className="app-header">
          <div className="brand-block">
            <div className="brand-lockup" aria-label="PC MANIA logo">
              <span className="brand-mark">PC</span>
              <span className="brand-name">MANIA</span>
            </div>
            <p className="eyebrow">PC MANIA / OPERACIONES</p>
            <h1>Panel de inventario</h1>
          </div>

          <div className="header-actions">
            <button
              type="button"
              className="ghost-button"
              onClick={() => {
                if (settingsState === 'open') {
                  setSettingsState('closing');
                  return;
                }

                if (settingsState === 'closing') {
                  return;
                }

                setSettingsState('open');
              }}
            >
              Preferencias
            </button>
            {!cargandoSesion && (logueado ? (
              <button type="button" onClick={() => signOut()}>Cerrar sesión</button>
            ) : (
              <button type="button" onClick={() => signInWithRedirect()}>Iniciar sesión</button>
            ))}
          </div>
        </header>

        {settingsState !== 'closed' && (
          <aside className={`settings-panel ${settingsState}`}>
            <div className="settings-header">
              <span>Preferencias</span>
              <button
                type="button"
                className="ghost-button small"
                onClick={() => {
                  setSettingsState('closing');
                }}
              >
                Cerrar
              </button>
            </div>
            <div className="setting-row">
              <div>
                <strong>Tema</strong>
                <small>{theme === 'dark' ? 'Oscuro' : 'Claro'}</small>
              </div>
              <button
                type="button"
                className="toggle-button"
                onClick={() => setTheme((current) => (current === 'dark' ? 'light' : 'dark'))}
              >
                {theme === 'dark' ? 'Claro' : 'Oscuro'}
              </button>
            </div>
          </aside>
        )}

        {cargandoSesion && <p className="mensaje">Verificando sesión...</p>}
        {!cargandoSesion && !logueado && <p className="mensaje">Inicia sesión como personal para agregar productos.</p>}
        {logueado && (
          <main>
            <form onSubmit={onSubmit} className="formulario">
              {editandoId !== null && <div className="formulario-heading"><span>Editando producto</span><button type="button" className="ghost-button small" onClick={() => { setEditandoId(null); setForm(FORM_INICIAL); }}>Cancelar</button></div>}
              <label>Nombre<input required value={form.nombre} onChange={(event) => setForm({ ...form, nombre: event.target.value })} /></label>
              <label>Marca<input required value={form.marca} onChange={(event) => setForm({ ...form, marca: event.target.value })} /></label>
              <label>Categoria<input required value={form.categoria} onChange={(event) => setForm({ ...form, categoria: event.target.value })} /></label>
              <label>Precio<input required type="number" min="0.01" step="0.01" value={form.precio} onChange={(event) => setForm({ ...form, precio: event.target.value })} /></label>
              <label>Stock<input required type="number" min="0" value={form.stock} onChange={(event) => setForm({ ...form, stock: event.target.value })} /></label>
              <button type="submit" disabled={enviando}>{enviando ? (editandoId !== null ? 'Guardando...' : 'Agregando...') : (editandoId !== null ? 'Guardar cambios' : 'Agregar al inventario')}</button>
            </form>
            {error && <p className="error">No se pudo completar la operación: {error}</p>}
            {mensajeExito && <p className="exito" role="status">{mensajeExito}</p>}
            <section className="inventario-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">AWS / MENSAJERÍA</p>
                  <h2>Operación de colas</h2>
                </div>
                <button type="button" className="refresh-button" disabled={cargandoMensajeria} onClick={() => void cargarMensajeria()}>
                  {cargandoMensajeria ? 'Consultando...' : 'Actualizar estado'}
                </button>
              </div>
              {mensajeMensajeria && <p className="mensaje">{mensajeMensajeria}</p>}
              {mensajeria && (
                <>
                  <p className="mensaje">Tópico: <strong>{mensajeria.topicName}</strong></p>
                  <ul className="lista-inventario">
                    {mensajeria.queues.map((queue) => {
                      const consumerKey = queue.name.replace('pc-mania-', '');
                      const running = consumidores[consumerKey] !== false;
                      return (
                        <li key={queue.name}>
                          <div>
                            <strong>{queue.name}</strong>
                            <small>
                              visibles {queue.status.visibleMessages} · en proceso {queue.status.inFlightMessages}
                              {' · '}antigüedad {queue.status.oldestMessageAgeSeconds}s
                              {' · '}DLQ {queue.status.deadLetterMessages}
                            </small>
                          </div>
                          <div className="item-actions">
                            <button type="button" className="ghost-button small" onClick={() => void ejecutarAccionMensajeria(
                              () => controlarConsumidor(consumerKey, running ? 'pause' : 'resume'),
                              running ? `${consumerKey}: consumidor pausado` : `${consumerKey}: consumidor reanudado`,
                            )}>
                              {running ? 'Pausar' : 'Reanudar'}
                            </button>
                            <button type="button" className="ghost-button small" onClick={() => void ejecutarAccionMensajeria(
                              () => reprocesarDlq(consumerKey),
                              `${consumerKey}: DLQ reprocesada`,
                            )}>
                              Reprocesar DLQ
                            </button>
                          </div>
                        </li>
                      );
                    })}
                  </ul>
                  <p className="mensaje">
                    Retención principal: {politicaRetencion.mainQueueRetention ?? 'no disponible'}
                    {' · '}DLQ: {politicaRetencion.dlqRetention ?? 'no disponible'}
                  </p>
                  <button type="button" className="ghost-button small" onClick={() => void ejecutarAccionMensajeria(
                    () => crearAlarmasDlq(),
                    'Alarmas de DLQ configuradas',
                  )}>
                    Configurar alarmas DLQ
                  </button>
                </>
              )}
            </section>
            <section className="inventario-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">AWS / KINESIS</p>
                  <h2>Streaming en tiempo real</h2>
                </div>
                <button type="button" className="refresh-button" disabled={cargandoMensajeria} onClick={() => void cargarMensajeria()}>
                  Actualizar Kinesis
                </button>
              </div>

              <div className="kinesis-summary">
                <div className="kinesis-stat">
                  <span>Streams</span>
                  <strong>{streamsKinesis.length}</strong>
                  <small>negocio y DLQ</small>
                </div>
                <div className="kinesis-stat">
                  <span>Shards activos</span>
                  <strong>{streamsKinesis.reduce((total, stream) => total + stream.openShardCount, 0)}</strong>
                  <small>distribución paralela</small>
                </div>
                <div className="kinesis-stat">
                  <span>Consumer groups</span>
                  <strong>{consumidoresKinesis.length}</strong>
                  <small>aplicaciones independientes</small>
                </div>
                <div className="kinesis-stat kinesis-stat-accent">
                  <span>Estado</span>
                  <strong>{streamsKinesis.some((stream) => stream.status === 'ACTIVE') ? 'Operativo' : 'Inactivo'}</strong>
                  <small>{streamsKinesis.length === 0 ? 'streams no creados' : 'topología conectada'}</small>
                </div>
              </div>

              <div className="kinesis-demo-panel">
                <div>
                  <p className="eyebrow">PRUEBA VISUAL</p>
                  <h3>Publicar evento</h3>
                  <small>El evento viajará al stream y aparecerá en los logs de ambos consumidores.</small>
                </div>
                <div className="kinesis-demo-controls">
                  <select
                    value={tipoEventoKinesis}
                    onChange={(event) => setTipoEventoKinesis(event.target.value as 'product.created' | 'product.updated' | 'order.created')}
                    aria-label="Tipo de evento Kinesis"
                  >
                    <option value="product.created">Producto creado</option>
                    <option value="product.updated">Producto actualizado</option>
                    <option value="order.created">Pedido creado</option>
                  </select>
                  <label className="kinesis-failure-toggle">
                    <input
                      type="checkbox"
                      checked={falloKinesis}
                      onChange={(event) => setFalloKinesis(event.target.checked)}
                    />
                    <span>Simular fallo</span>
                  </label>
                  <label
                    className={`kinesis-failure-toggle ${!ultimoEventIdKinesis ? 'disabled' : ''}`}
                    title={!ultimoEventIdKinesis ? 'Publica primero un evento normal' : 'Reutiliza el último eventId'}
                  >
                    <input
                      type="checkbox"
                      checked={repetirEventIdKinesis}
                      disabled={!ultimoEventIdKinesis}
                      onChange={(event) => setRepetirEventIdKinesis(event.target.checked)}
                    />
                    <span>Repetir eventId</span>
                  </label>
                  <button type="button" className="kinesis-publish-button" disabled={publicandoKinesis} onClick={() => void publicarEventoDemo()}>
                    {publicandoKinesis ? 'Publicando...' : 'Publicar en Kinesis'}
                  </button>
                </div>
              </div>
              <div className="kinesis-actions">
                <button type="button" className="ghost-button small" onClick={() => void ejecutarAccionMensajeria(
                  () => crearTopologiaKinesis(),
                  'Streams de Kinesis creados o ya existentes',
                )}>
                  Crear streams
                </button>
                <button type="button" className="ghost-button small" onClick={() => void ejecutarAccionMensajeria(
                  () => crearAlarmasIteratorAge(),
                  'Alarmas IteratorAge configuradas',
                )}>
                  Crear alarmas
                </button>
              </div>

              {streamsKinesis.length > 0 ? (
                <div className="kinesis-stream-grid">
                  {streamsKinesis.map((stream) => (
                    <article className="kinesis-stream-card" key={stream.key}>
                      <div className="kinesis-stream-header">
                        <div>
                          <span className={`kinesis-status-dot ${stream.status === 'ACTIVE' ? 'active' : ''}`} />
                          <strong>{stream.name}</strong>
                        </div>
                        <span className="kinesis-badge">{stream.status}</span>
                      </div>
                      <div className="kinesis-shards">
                        {Array.from({ length: stream.openShardCount }, (_, index) => (
                          <span className="kinesis-shard" key={index}>S{index + 1}</span>
                        ))}
                      </div>
                      <dl className="kinesis-details">
                        <div><dt>Shards</dt><dd>{stream.openShardCount}/{stream.configuredShardCount}</dd></div>
                        <div><dt>Retención</dt><dd>{stream.retentionHours} h</dd></div>
                        <div>
                          <dt>Iterator age</dt>
                          <dd>{stream.iteratorAgeMilliseconds == null ? 'Sin datos' : `${Math.round(stream.iteratorAgeMilliseconds)} ms`}</dd>
                        </div>
                      </dl>
                      <div className="kinesis-card-actions">
                        <button type="button" onClick={() => void ejecutarAccionMensajeria(
                          () => actualizarShardsKinesis(stream.key, stream.configuredShardCount === 3 ? 6 : 3),
                          `${stream.name}: shards actualizados`,
                        )}>
                          Alternar shards
                        </button>
                        <button type="button" onClick={() => void ejecutarAccionMensajeria(
                          () => actualizarRetencionKinesis(stream.key, stream.retentionHours >= 168 ? 24 : 168),
                          `${stream.name}: retención actualizada`,
                        )}>
                          Alternar retención
                        </button>
                        <button type="button" className="kinesis-delete" onClick={() => void ejecutarAccionMensajeria(
                          () => eliminarStreamKinesis(stream.key),
                          `${stream.name}: solicitud de eliminación enviada`,
                        )}>
                          Eliminar
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              ) : (
                <p className="mensaje">No hay streams consultables. Kinesis puede estar deshabilitado o los streams todavía no existen.</p>
              )}
              {consumidoresKinesis.length > 0 && (
                <div className="kinesis-consumer-grid">
                  {consumidoresKinesis.map((consumer) => (
                    <article className="kinesis-consumer-card" key={consumer.applicationName}>
                      <div className="kinesis-consumer-heading">
                        <span className="kinesis-consumer-icon">KCL</span>
                        <div>
                          <strong>{consumer.applicationName}</strong>
                          <small>{consumer.streamName}</small>
                        </div>
                      </div>
                      <small className="kinesis-table">
                        DynamoDB: {consumer.leaseTable}
                      </small>
                      <div className="kinesis-consumer-state">
                        <span className={consumer.running ? 'running' : ''}>
                          {consumer.running ? 'Proceso activo' : 'Detenido'}
                        </span>
                        <small>
                          {consumer.message}
                        </small>
                      </div>
                      <p className="kinesis-lease-summary">
                        {consumer.leaseCount} leases · {consumer.assignedShardCount} shards asignados
                      </p>
                      {consumer.leases.length > 0 && (
                        <ul className="kinesis-lease-list">
                          {consumer.leases.map((lease) => (
                            <li key={lease.leaseKey}>
                              <strong>{lease.leaseKey}</strong>
                              <span>Checkpoint: {lease.checkpoint ?? 'sin checkpoint'}</span>
                              <small>
                                {lease.leaseOwner ? `Worker ${lease.leaseOwner.slice(0, 12)}` : 'Sin worker asignado'}
                              </small>
                            </li>
                          ))}
                        </ul>
                      )}
                    </article>
                  ))}
                </div>
              )}
            </section>
            <section className="inventario-section">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">INVENTARIO ACTUAL</p>
                  <h2>Productos registrados</h2>
                </div>
                <div className="section-heading-actions">
                  <span>{catalogoFiltrado.length} de {catalogo.length} productos</span>
                  <button type="button" className="refresh-button" disabled={cargandoCatalogo} onClick={() => void cargarCatalogo()} aria-label="Refrescar inventario">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" viewBox="0 0 16 16" aria-hidden="true">
                      <path d="M11.534 7h3.932a.25.25 0 0 1 .192.41l-1.966 2.36a.25.25 0 0 1-.384 0l-1.966-2.36a.25.25 0 0 1 .192-.41zm-11 2h3.932a.25.25 0 0 0 .192-.41L2.692 6.23a.25.25 0 0 0-.384 0L.342 8.59A.25.25 0 0 0 .534 9z" />
                      <path fillRule="evenodd" d="M8 3c-1.552 0-2.94.707-3.857 1.818a.5.5 0 1 1-.771-.636A6.002 6.002 0 0 1 13.917 7H12.9A5.002 5.002 0 0 0 8 3zM3.1 9a5.002 5.002 0 0 0 8.757 2.182.5.5 0 1 1 .771.636A6.002 6.002 0 0 1 2.083 9H3.1z" />
                    </svg>
                    {cargandoCatalogo ? 'Refrescando...' : 'Refrescar'}
                  </button>
                </div>
              </div>
              <div className="filtros-catalogo" aria-label="Filtros del inventario">
                <input
                  type="search"
                  placeholder="Buscar por nombre, marca o categoría"
                  value={busqueda}
                  onChange={(event) => setBusqueda(event.target.value)}
                  aria-label="Buscar productos"
                />
                <FiltroSelect value={categoriaSeleccionada} placeholder="Todas las categorías" options={categorias} ariaLabel="Filtrar por categoría" onChange={setCategoriaSeleccionada} />
                <FiltroSelect value={marcaSeleccionada} placeholder="Todas las marcas" options={marcas} ariaLabel="Filtrar por marca" onChange={setMarcaSeleccionada} />
                <FiltroSelect value={orden} placeholder="Ordenar productos" options={['nombre', 'precio-asc', 'precio-desc', 'stock-desc']} ariaLabel="Ordenar productos" onChange={(value) => setOrden(value as OrdenProductos)} />
              </div>
              {cargandoCatalogo && <p className="mensaje">Cargando inventario...</p>}
              {!cargandoCatalogo && catalogo.length === 0 && <p className="mensaje">No hay productos registrados.</p>}
              {!cargandoCatalogo && catalogo.length > 0 && catalogoFiltrado.length === 0 && <p className="mensaje">No hay productos que coincidan con los filtros.</p>}
              <ul className="lista-inventario">
                {productosVisibles.map((producto) => (
                  <li key={producto.id}>
                    <div>
                      <strong>{producto.nombre}</strong>
                      <small>{producto.marca} · {producto.categoria} · ${producto.precio.toLocaleString('es-CL')} · stock {producto.stock}</small>
                    </div>
                    <div className="item-actions">
                      <button type="button" className="ghost-button small" onClick={() => iniciarEdicion(producto)}>Editar</button>
                      <button type="button" className="danger-button" disabled={eliminandoId === producto.id} onClick={() => setProductoParaEliminar(producto)}>
                        {eliminandoId === producto.id ? 'Eliminando...' : 'Eliminar'}
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
              {totalPaginas > 1 && (
                <div className="paginacion" aria-label="Paginación del inventario">
                  <button type="button" className="ghost-button small" disabled={paginaActual === 1} onClick={() => setPagina((actual) => Math.max(1, actual - 1))}>Anterior</button>
                  <span>Página {paginaActual} de {totalPaginas}</span>
                  <button type="button" className="ghost-button small" disabled={paginaActual === totalPaginas} onClick={() => setPagina((actual) => Math.min(totalPaginas, actual + 1))}>Siguiente</button>
                </div>
              )}
            </section>
            {agregados.length > 0 && (
              <section>
                <h2>Agregados en esta sesión</h2>
                <ul className="lista-agregados">
                  {agregados.map((producto) => (
                    <li key={producto.id}>#{producto.id} - {producto.nombre} ({producto.marca}) - ${producto.precio.toLocaleString('es-CL')} - stock {producto.stock}</li>
                  ))}
                </ul>
              </section>
            )}
          </main>
        )}
        {productoParaEliminar && (
          <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setProductoParaEliminar(null); }}>
            <section className="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="confirmar-eliminacion">
              <p className="eyebrow">ACCION IRREVERSIBLE</p>
              <h2 id="confirmar-eliminacion">¿Eliminar producto?</h2>
              <p>Se eliminará <strong>{productoParaEliminar.nombre}</strong> del inventario.</p>
              <div className="modal-actions">
                <button type="button" className="ghost-button" onClick={() => setProductoParaEliminar(null)}>Cancelar</button>
                <button type="button" className="danger-button" onClick={() => void onEliminarConfirmado()}>Eliminar producto</button>
              </div>
            </section>
          </div>
        )}
      </div>
    </div>
    {createPortal(
      <>
        {resultadoKinesis && (
          <div className={`kinesis-result ${tipoResultadoKinesis ?? 'processed'}${cerrandoResultadoKinesis ? ' closing' : ''}`} role="status">
            <span>{falloKinesis ? 'Fallo controlado enviado al DLQ' : 'Evento publicado correctamente'}</span>
            <strong>{resultadoKinesis}</strong>
          </div>
        )}
        {errorKinesis && (
          <div className={`kinesis-result error${cerrandoResultadoKinesis ? ' closing' : ''}`} role="alert">
            <span>Error de publicación</span>
            <strong>{errorKinesis}</strong>
          </div>
        )}
        {estadosEventosKinesis.length > 0 && (
          <aside className="kinesis-event-statuses" aria-label="Estado de eventos Kinesis">
            <div className="kinesis-event-status-heading">
              <p className="eyebrow">ESTADO DE EVENTOS</p>
              <button type="button" className="ghost-button small" onClick={() => void cargarMensajeria()}>
                Actualizar
              </button>
            </div>
            <div className="kinesis-event-status-grid">
              {estadosEventosKinesis.slice(0, 6).map((item) => (
                <article className={`kinesis-event-status ${item.status.toLowerCase()}`} key={`${item.eventId}:${item.applicationName}`}>
                  <span className="kinesis-event-status-label">{item.status}</span>
                  <strong>{item.eventId}</strong>
                  <small>{item.applicationName} · {item.sequenceNumber}</small>
                </article>
              ))}
            </div>
          </aside>
        )}
      </>,
      document.body,
    )}
    </>
  );
}

export default App;
