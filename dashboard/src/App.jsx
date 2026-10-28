import { useState, useEffect, useCallback } from 'react'

export default function App() {
  const [overview, setOverview] = useState({
    gatewayStatus: 'UP',
    redisStatus: 'UP',
    requestsPerSecond: 0,
    successRate: 100,
    blockedRequests: 0,
    averageLatency: 0
  })

  const [liveStatus, setLiveStatus] = useState({
    gateway: 'UP',
    redis: 'UP',
    authentication: 'UP',
    routing: 'UP',
    rateLimiter: 'UP'
  })

  const [rateLimiter, setRateLimiter] = useState({
    algorithm: 'TOKEN_BUCKET',
    allowedRequests: 0,
    blockedRequests: 0,
    averageDecisionLatency: 0,
    redisLatency: 0
  })

  const [systemInfo, setSystemInfo] = useState({
    application: 'Distributed API Gateway',
    version: '1.0.0-SNAPSHOT',
    javaVersion: '21',
    springBootVersion: '3.3.2',
    uptime: '0d 0h 0m'
  })

  const [lastUpdated, setLastUpdated] = useState(() => new Date())
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [countdown, setCountdown] = useState(5)

  const fetchDashboardData = useCallback(async (isManual = false) => {
    try {
      if (isManual) setLoading(true)
      const [ovRes, liveRes, rlRes, sysRes] = await Promise.all([
        fetch('/api/v1/dashboard/overview').then(r => r.json()),
        fetch('/api/v1/dashboard/live').then(r => r.json()),
        fetch('/api/v1/dashboard/ratelimiter').then(r => r.json()),
        fetch('/api/v1/dashboard/system').then(r => r.json())
      ])

      if (ovRes?.data) setOverview(ovRes.data)
      if (liveRes?.data) setLiveStatus(liveRes.data)
      if (rlRes?.data) setRateLimiter(rlRes.data)
      if (sysRes?.data) setSystemInfo(sysRes.data)

      setError(null)
      setLastUpdated(new Date())
      setCountdown(5)
    } catch (err) {
      console.warn('Dashboard poll error:', err)
      setError('Unable to reach Gateway backend. Retrying in 5 seconds...')
    } finally {
      if (isManual) setLoading(false)
    }
  }, [])

  useEffect(() => {
    let active = true

    async function loadInitial() {
      try {
        const [ovRes, liveRes, rlRes, sysRes] = await Promise.all([
          fetch('/api/v1/dashboard/overview').then(r => r.json()),
          fetch('/api/v1/dashboard/live').then(r => r.json()),
          fetch('/api/v1/dashboard/ratelimiter').then(r => r.json()),
          fetch('/api/v1/dashboard/system').then(r => r.json())
        ])
        if (!active) return
        if (ovRes?.data) setOverview(ovRes.data)
        if (liveRes?.data) setLiveStatus(liveRes.data)
        if (rlRes?.data) setRateLimiter(rlRes.data)
        if (sysRes?.data) setSystemInfo(sysRes.data)
        setError(null)
      } catch (err) {
        if (!active) return
        console.warn('Initial load error:', err)
        setError('Unable to reach Gateway backend. Retrying in 5 seconds...')
      }
    }

    loadInitial()

    const timer = setInterval(() => {
      setCountdown(prev => {
        if (prev <= 1) {
          fetchDashboardData(false)
          return 5
        }
        return prev - 1
      })
    }, 1000)

    return () => {
      active = false
      clearInterval(timer)
    }
  }, [fetchDashboardData])

  const totalEvaluations = (rateLimiter.allowedRequests || 0) + (rateLimiter.blockedRequests || 0)
  const blockPercent = totalEvaluations > 0
    ? Math.round((rateLimiter.blockedRequests / totalEvaluations) * 1000) / 10
    : 0

  return (
    <div className="dashboard-container">
      {/* Header */}
      <header className="dashboard-header">
        <div className="header-brand">
          <div className="brand-icon">⚡</div>
          <div>
            <h1 className="brand-title">Distributed Gateway Ops</h1>
            <p className="brand-subtitle">Real-time Telemetry, Observability & Rate Limiting</p>
          </div>
        </div>

        <div className="header-controls">
          <div className={`status-pill ${overview.gatewayStatus.toLowerCase()}`}>
            <span className="status-dot"></span>
            {overview.gatewayStatus === 'UP' ? 'All Systems Operational' : 'System Degraded'}
          </div>

          <button
            className="refresh-btn"
            onClick={() => fetchDashboardData(true)}
            disabled={loading}
          >
            {loading ? 'Refreshing...' : `Refresh (${countdown}s)`}
          </button>
        </div>
      </header>

      {/* Error alert if disconnected */}
      {error && (
        <div style={{
          background: 'rgba(244, 63, 94, 0.1)',
          border: '1px solid var(--status-down)',
          color: 'var(--status-down)',
          padding: '12px 18px',
          borderRadius: '10px',
          marginBottom: '20px',
          fontSize: '13px'
        }}>
          ⚠️ {error}
        </div>
      )}

      {/* Primary KPI Row */}
      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-label">Gateway Status</div>
          <div className="kpi-value" style={{ color: overview.gatewayStatus === 'UP' ? 'var(--status-up)' : 'var(--status-down)' }}>
            {overview.gatewayStatus}
          </div>
          <div className="kpi-subtext">Local Instance Active</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Redis Cluster</div>
          <div className="kpi-value" style={{ color: overview.redisStatus === 'UP' ? 'var(--status-up)' : 'var(--status-down)' }}>
            {overview.redisStatus}
          </div>
          <div className="kpi-subtext">Distributed Shared State</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Throughput</div>
          <div className="kpi-value" style={{ color: 'var(--accent-cyan)' }}>
            {overview.requestsPerSecond} <span style={{ fontSize: '16px', fontWeight: 500 }}>RPS</span>
          </div>
          <div className="kpi-subtext">Gateway Request Rate</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Success Rate</div>
          <div className="kpi-value" style={{ color: overview.successRate >= 99 ? 'var(--status-up)' : 'var(--status-warn)' }}>
            {overview.successRate}%
          </div>
          <div className="progress-container">
            <div className="progress-bar emerald" style={{ width: `${Math.min(100, overview.successRate)}%` }}></div>
          </div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Blocked Requests</div>
          <div className="kpi-value" style={{ color: 'var(--status-down)' }}>
            {overview.blockedRequests}
          </div>
          <div className="kpi-subtext">{blockPercent}% Rate Limiter Drop Ratio</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-label">Average Latency</div>
          <div className="kpi-value">
            {overview.averageLatency} <span style={{ fontSize: '16px', fontWeight: 500 }}>ms</span>
          </div>
          <div className="kpi-subtext">Rolling Gateway Latency</div>
        </div>
      </div>

      {/* Subsystem Health Matrix */}
      <h2 className="section-title">Component Health Matrix</h2>
      <div className="subsystems-grid">
        {Object.entries(liveStatus).map(([key, status]) => (
          <div className="subsystem-card" key={key}>
            <span className="subsystem-name" style={{ textTransform: 'capitalize' }}>
              {key === 'rateLimiter' ? 'Rate Limiter' : key}
            </span>
            <span className={`status-pill ${status.toLowerCase()}`} style={{ padding: '4px 10px', fontSize: '11px' }}>
              <span className="status-dot"></span>
              {status}
            </span>
          </div>
        ))}
      </div>

      {/* Deep-Dive Panels */}
      <div className="details-grid">
        {/* Rate Limiter Details */}
        <div className="panel-card">
          <div className="panel-header">
            <h3 className="panel-title">Rate Limiter Telemetry</h3>
            <span className="badge">{rateLimiter.algorithm}</span>
          </div>

          <div className="metric-row">
            <span className="metric-name">Active Strategy Algorithm</span>
            <span className="metric-val">{rateLimiter.algorithm}</span>
          </div>
          <div className="metric-row">
            <span className="metric-name">Allowed Requests</span>
            <span className="metric-val" style={{ color: 'var(--status-up)' }}>
              {rateLimiter.allowedRequests.toLocaleString()}
            </span>
          </div>
          <div className="metric-row">
            <span className="metric-name">Blocked Requests (429)</span>
            <span className="metric-val" style={{ color: 'var(--status-down)' }}>
              {rateLimiter.blockedRequests.toLocaleString()}
            </span>
          </div>
          <div className="metric-row">
            <span className="metric-name">Decision Evaluation Latency</span>
            <span className="metric-val">{rateLimiter.averageDecisionLatency} ms</span>
          </div>
          <div className="metric-row">
            <span className="metric-name">Redis Roundtrip Latency</span>
            <span className="metric-val">{rateLimiter.redisLatency} ms</span>
          </div>

          <div style={{ marginTop: '14px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', color: 'var(--text-muted)' }}>
              <span>Allowed vs Blocked Ratio</span>
              <span>{blockPercent}% Blocked</span>
            </div>
            <div className="progress-container">
              <div className="progress-bar rose" style={{ width: `${Math.min(100, blockPercent)}%` }}></div>
            </div>
          </div>
        </div>

        {/* Runtime & Host Environment */}
        <div className="panel-card">
          <div className="panel-header">
            <h3 className="panel-title">Runtime & Host Environment</h3>
            <span className="badge">Java {systemInfo.javaVersion}</span>
          </div>

          <div className="metric-row">
            <span className="metric-name">Application Name</span>
            <span className="metric-val">{systemInfo.application}</span>
          </div>
          <div className="metric-row">
            <span className="metric-name">Artifact Version</span>
            <span className="metric-val">{systemInfo.version}</span>
          </div>
          <div className="metric-row">
            <span className="metric-name">Java Runtime Version</span>
            <span className="metric-val">{systemInfo.javaVersion}</span>
          </div>
          <div className="metric-row">
            <span className="metric-name">Spring Boot Framework</span>
            <span className="metric-val">{systemInfo.springBootVersion}</span>
          </div>
          <div className="metric-row">
            <span className="metric-name">Gateway Uptime</span>
            <span className="metric-val" style={{ color: 'var(--accent-cyan)' }}>{systemInfo.uptime}</span>
          </div>

          <div style={{ marginTop: '16px' }}>
            <div className="notice-card">
              <strong>Local Isolation Note:</strong> Metrics reflect this gateway instance and its connected Redis shared state. Cluster-wide peer health polling is deferred pending peer discovery specification.
            </div>
          </div>
        </div>

        {/* Route Analytics Status */}
        <div className="panel-card">
          <div className="panel-header">
            <h3 className="panel-title">Route Analytics</h3>
            <span className="badge" style={{ background: 'rgba(245, 158, 11, 0.15)', color: 'var(--status-warn)', borderColor: 'rgba(245, 158, 11, 0.3)' }}>
              Sprint 19/20
            </span>
          </div>

          <div style={{ padding: '24px 0', textAlign: 'center' }}>
            <div style={{ fontSize: '32px', marginBottom: '10px' }}>🧭</div>
            <div style={{ fontSize: '14px', fontWeight: 600, marginBottom: '6px' }}>Dynamic Routing Inactive</div>
            <p style={{ fontSize: '13px', color: 'var(--text-muted)', maxWidth: '300px', margin: '0 auto', lineHeight: 1.5 }}>
              Per-route throughput, path-level error distribution, and latency percentiles will populate upon reverse-proxy route table activation in Sprint 19/20.
            </p>
          </div>
        </div>
      </div>

      {/* Footer */}
      <footer className="dashboard-footer">
        <div>
          Distributed API Gateway • Sprint 18 Dashboard &amp; Analytics
        </div>
        <div>
          Last updated: {lastUpdated.toLocaleTimeString()} (5s interval)
        </div>
      </footer>
    </div>
  )
}
