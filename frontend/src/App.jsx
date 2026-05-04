import { useEffect, useState } from 'react'

const STATUS_COLORS = {
  TODO:  '#94a3b8',
  DOING: '#f59e0b',
  DONE:  '#10b981'
}

async function jsonFetch(url, opts = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) },
    ...opts
  })
  if (!res.ok && res.status !== 204) {
    throw new Error(`${res.status} ${res.statusText}`)
  }
  return res.status === 204 ? null : res.json()
}

export default function App() {
  const [users, setUsers] = useState([])
  const [tasks, setTasks] = useState([])
  const [error, setError] = useState(null)
  const [details, setDetails] = useState(null)

  const reload = async () => {
    try {
      const [u, t] = await Promise.all([
        jsonFetch('/api/users'),
        jsonFetch('/api/tasks')
      ])
      setUsers(u); setTasks(t); setError(null)
    } catch (e) { setError(e.message) }
  }

  useEffect(() => { reload() }, [])

  const userMap = Object.fromEntries(users.map(u => [u.id, u]))

  const addUser = async (e) => {
    e.preventDefault()
    const f = e.target
    try {
      await jsonFetch('/api/users', {
        method: 'POST',
        body: JSON.stringify({ username: f.username.value, email: f.email.value })
      })
      f.reset(); reload()
    } catch (e) { setError(e.message) }
  }

  const addTask = async (e) => {
    e.preventDefault()
    const f = e.target
    try {
      await jsonFetch('/api/tasks', {
        method: 'POST',
        body: JSON.stringify({
          title: f.title.value,
          description: f.description.value,
          status: f.status.value,
          assigneeId: Number(f.assigneeId.value)
        })
      })
      f.reset(); reload()
    } catch (e) { setError(e.message) }
  }

  const delUser = async (id) => {
    try { await jsonFetch(`/api/users/${id}`, { method: 'DELETE' }); reload() }
    catch (e) { setError(e.message) }
  }
  const delTask = async (id) => {
    try { await jsonFetch(`/api/tasks/${id}`, { method: 'DELETE' }); reload() }
    catch (e) { setError(e.message) }
  }

  const showFull = async (id) => {
    try { setDetails(await jsonFetch(`/api/tasks/${id}/full`)) }
    catch (e) { setError(e.message) }
  }

  return (
    <div className="app">
      <header>
        <h1>Task Manager</h1>
        <p className="subtitle">
          Microservices Spring Boot · PostgreSQL · Kubernetes — par Younes OUAMAR
        </p>
      </header>

      {error && <div className="error">⚠ {error}</div>}

      <div className="grid">
        <section className="card">
          <h2>Utilisateurs <span className="count">{users.length}</span></h2>
          <ul className="list">
            {users.map(u => (
              <li key={u.id}>
                <div>
                  <strong>{u.username}</strong>
                  <span className="muted"> · {u.email}</span>
                </div>
                <button className="ghost" onClick={() => delUser(u.id)}>×</button>
              </li>
            ))}
          </ul>
          <form onSubmit={addUser} className="form">
            <input name="username" placeholder="username" required />
            <input name="email" type="email" placeholder="email" required />
            <button type="submit">Ajouter</button>
          </form>
        </section>

        <section className="card">
          <h2>Tâches <span className="count">{tasks.length}</span></h2>
          <ul className="list">
            {tasks.map(t => (
              <li key={t.id}>
                <div>
                  <span className="status-dot" style={{ background: STATUS_COLORS[t.status] }} />
                  <strong>{t.title}</strong>
                  <span className="muted"> · @{userMap[t.assigneeId]?.username ?? '?'}</span>
                </div>
                <div>
                  <button className="ghost" onClick={() => showFull(t.id)}>Détails</button>
                  <button className="ghost" onClick={() => delTask(t.id)}>×</button>
                </div>
              </li>
            ))}
          </ul>
          <form onSubmit={addTask} className="form">
            <input name="title" placeholder="titre" required />
            <input name="description" placeholder="description" />
            <select name="status" defaultValue="TODO">
              <option value="TODO">TODO</option>
              <option value="DOING">DOING</option>
              <option value="DONE">DONE</option>
            </select>
            <select name="assigneeId" required defaultValue="">
              <option value="" disabled>assigner à…</option>
              {users.map(u => (
                <option key={u.id} value={u.id}>{u.username}</option>
              ))}
            </select>
            <button type="submit">Ajouter</button>
          </form>
        </section>
      </div>

      {details && (
        <div className="modal-bg" onClick={() => setDetails(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3>Tâche #{details.id} — données enrichies</h3>
            <p className="muted">
              Réponse de <code>GET /api/tasks/{details.id}/full</code> →
              tasks-service appelle users-service via DNS Kubernetes pour résoudre l'assignee.
            </p>
            <pre>{JSON.stringify(details, null, 2)}</pre>
            <button onClick={() => setDetails(null)}>Fermer</button>
          </div>
        </div>
      )}

      <footer>
        <span>Gateway : <code>taskmanager.local</code> (Ingress NGINX)</span>
        <span>Backends : users-service · tasks-service · postgres</span>
      </footer>
    </div>
  )
}
