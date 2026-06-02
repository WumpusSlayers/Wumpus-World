/* ==========================================
   👾 Wumpus World AI - Premium Core Script
   🔌 REST API Integration & Smooth Auto Play Loop
   ========================================== */

// 1. API Configuration
const BASE_URL = 'http://localhost:8080/api/game';

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 6000,
  headers: { 'Content-Type': 'application/json' }
});

// 2. Application State
const state = {
  userId: '',
  isPlaying: false,
  board: null,
  kbSummary: null,
  isAutoPlaying: false,
  autoPlayIntervalId: null,
  logs: []
};

// 3. DOM Elements Cache
const DOM = {
  usernameInput: document.getElementById('username-input'),
  btnStart: document.getElementById('btn-start'),
  simulationControls: document.getElementById('simulation-controls'),
  btnAutoStep: document.getElementById('btn-auto-step'),
  btnAutoPlay: document.getElementById('btn-auto-play'),
  btnReset: document.getElementById('btn-reset'),
  gameMap: document.getElementById('game-map'),
  kbMap: document.getElementById('kb-map'),
  terminalLogs: document.getElementById('terminal-logs'),
  btnClearLogs: document.getElementById('btn-clear-logs'),
  setupGroup: document.getElementById('setup-group'),
  
  // Header badges
  headerStats: document.getElementById('header-stats'),
  statUserId: document.getElementById('stat-userid'),
  statArrows: document.getElementById('stat-arrows'),
  statWumpusStatus: document.getElementById('stat-wumpus-status'),
  statGameStatus: document.getElementById('stat-game-status'),
  
  // Manual buttons
  btnForward: document.getElementById('btn-forward'),
  btnLeft: document.getElementById('btn-left'),
  btnRight: document.getElementById('btn-right'),
  btnGrab: document.getElementById('btn-grab'),
  btnShoot: document.getElementById('btn-shoot'),
  btnClimb: document.getElementById('btn-climb')
};

// 4. Logging Engine
function addLog(prefix, content, type = '') {
  const time = new Date().toLocaleTimeString();
  const logEntry = document.createElement('div');
  logEntry.className = `log-entry ${type ? 'log-' + type : ''}`;
  
  logEntry.innerHTML = `
    <span class="log-time">[${time}]</span>
    <span class="log-prefix font-bold">[${prefix}]</span>
    <span class="log-content">${content}</span>
  `;
  
  DOM.terminalLogs.appendChild(logEntry);
  DOM.terminalLogs.scrollTop = DOM.terminalLogs.scrollHeight;
}

// 5. Draw Game Map (4x4 Grid)
function drawGameMap() {
  DOM.gameMap.innerHTML = '';
  if (!state.board) return;
  
  const cells = state.board.grid.cells;
  const agentPos = state.board.agentPosition;
  const agentDir = state.board.agentDirection || 'EAST';
  const kbDetails = state.kbSummary ? state.kbSummary.cellDetails || [] : [];
  
  // Cartesian y-coordinates (4 down to 1)
  for (let y = 4; y >= 1; y--) {
    // x-coordinates (1 up to 4)
    for (let x = 1; x <= 4; x++) {
      const cell = cells[x - 1][y - 1];
      const tile = document.createElement('div');
      tile.className = 'tile';
      tile.dataset.x = x;
      tile.dataset.y = y;
      
      // Special Starting cell (1,1) color class
      const isStartCell = (x === 1 && y === 1);
      if (isStartCell) {
        tile.classList.add('start-cell');
      }
      
      const isAgentHere = (agentPos.x === x && agentPos.y === y);
      const kbCell = kbDetails.find(c => c.x === x && c.y === y);
      
      if (cell.visited) {
        tile.classList.add('visited');
        
        // Render Senses
        const adjacentPercepts = getAdjacentPercepts(x, y);
        if (adjacentPercepts.breeze) {
          tile.classList.add('breeze-effect');
          const windGust = document.createElement('div');
          windGust.className = 'wind-gust';
          windGust.title = '바람이 느껴집니다 (주변에 구덩이가 있음)';
          tile.appendChild(windGust);
        }
        if (adjacentPercepts.stench) {
          tile.classList.add('stench-effect');
          
          // Create the 4 waves representing front, back, left, right (north, south, east, west)
          const waveTop = document.createElement('div');
          waveTop.className = 'stench-wave wave-top';
          waveTop.title = '북쪽에서 괴물 냄새가 흘러옵니다';
          
          const waveBottom = document.createElement('div');
          waveBottom.className = 'stench-wave wave-bottom';
          waveBottom.title = '남쪽에서 괴물 냄새가 흘러옵니다';
          
          const waveLeft = document.createElement('div');
          waveLeft.className = 'stench-wave wave-left';
          waveLeft.title = '서쪽에서 괴물 냄새가 흘러옵니다';
          
          const waveRight = document.createElement('div');
          waveRight.className = 'stench-wave wave-right';
          waveRight.title = '동쪽에서 괴물 냄새가 흘러옵니다';
          
          tile.appendChild(waveTop);
          tile.appendChild(waveBottom);
          tile.appendChild(waveLeft);
          tile.appendChild(waveRight);
        }
        
        // Render Objects
        if (cell.hasGold) {
          tile.classList.add('gold-effect');
          const goldSparkle = document.createElement('div');
          goldSparkle.className = 'gold-sparkle';
          goldSparkle.title = '금 발견! 눈부시게 빛납니다 ✨';
          tile.appendChild(goldSparkle);
          
          tile.innerHTML += '<span class="tile-badge" title="Gold">✨👑</span>';
        } else if (cell.hasWumpus) {
          const wumpusIcon = state.board.wumpusAlive ? '👾' : '💀';
          tile.innerHTML += `<span class="tile-badge" title="Wumpus">${wumpusIcon}</span>`;
        } else if (cell.hasPit) {
          tile.innerHTML += '<span class="tile-badge" title="Pit">🕳️</span>';
        }
      } else {
        // Unvisited fog or helper
        if (kbCell && kbCell.definitePit) {
          tile.classList.add('definite-pit-helper');
          tile.innerHTML = '<span class="tile-badge-helper" title="추론으로 확정된 구덩이">🕳️⚠️</span>';
        } else if (kbCell && kbCell.definiteWumpus) {
          tile.classList.add('definite-wumpus-helper');
          tile.innerHTML = '<span class="tile-badge-helper" title="추론으로 확정된 웜파스">👾⚠️</span>';
        } else {
          tile.innerHTML = '<span class="fog-cloud" title="미탐색">☁️</span>';
        }
      }
      
      // Render Agent on top
      if (isAgentHere) {
        tile.classList.add('safe-cell');
        
        // Create Agent Shaking Wrapper for wind breeze
        const agentWrapper = document.createElement('div');
        agentWrapper.className = 'agent-wrapper';
        
        const adjacentPercepts = getAdjacentPercepts(x, y);
        if (adjacentPercepts.breeze) {
          agentWrapper.classList.add('wind-shake');
          agentWrapper.title = '바람 때문에 에이전트가 위태롭게 흔들립니다!';
        }
        
        const agentSprite = document.createElement('span');
        agentSprite.className = `agent-sprite dir-${agentDir.toLowerCase()}`;
        
        // Grabbed Gold check: show gold next to agent emoticon
        const hasGoldGrabbed = (state.board && (state.board.status === 'WIN' || state.board.status === 'ESCAPED'));
        const agentEmoji = hasGoldGrabbed ? '🤠👑' : '🤠';
        
        agentSprite.innerHTML = `${agentEmoji}<span class="direction-pointer">➜</span>`;
        agentSprite.title = `에이전트 (바라보는 방향: ${agentDir}${hasGoldGrabbed ? ' | 금 보유 중' : ''})`;
        
        agentWrapper.appendChild(agentSprite);
        tile.appendChild(agentWrapper);
      }
      
      DOM.gameMap.appendChild(tile);
    }
  }
}

// Compute Breeze/Stench from cells adjacent to Wumpus/Pit on Grid
function getAdjacentPercepts(x, y) {
  const cells = state.board.grid.cells;
  let breeze = false;
  let stench = false;
  
  const directions = [[0, 1], [0, -1], [1, 0], [-1, 0]];
  for (const [dx, dy] of directions) {
    const nx = x + dx;
    const ny = y + dy;
    if (nx >= 1 && nx <= 4 && ny >= 1 && ny <= 4) {
      const neighbor = cells[nx - 1][ny - 1];
      if (neighbor.hasPit) breeze = true;
      if (neighbor.hasWumpus && state.board.wumpusAlive) stench = true;
    }
  }
  return { breeze, stench };
}

// 6. Draw KB Snapshot Map (4x4 Grid)
function drawKbMap() {
  DOM.kbMap.innerHTML = '';
  if (!state.kbSummary) return;
  
  const details = state.kbSummary.cellDetails || [];
  
  // Cartesian y-coordinates (4 down to 1)
  for (let y = 4; y >= 1; y--) {
    // x-coordinates (1 up to 4)
    for (let x = 1; x <= 4; x++) {
      const cell = details.find(c => c.x === x && c.y === y) || {
        visited: false, safe: false, possiblePit: false, definitePit: false, possibleWumpus: false, definiteWumpus: false
      };
      
      const tile = document.createElement('div');
      tile.className = 'kb-tile';
      tile.dataset.x = x;
      tile.dataset.y = y;
      
      // Special Starting cell (1,1) color class
      const isStartCell = (x === 1 && y === 1);
      if (isStartCell) {
        tile.classList.add('start-cell');
      }
      
      if (cell.visited) {
        tile.classList.add('visited');
      }
      if (cell.safe) {
        tile.classList.add('safe');
      }
      
      // Populate text badges inside KB tile
      const badgeContainer = document.createElement('div');
      badgeContainer.style.display = 'flex';
      badgeContainer.style.flexDirection = 'column';
      badgeContainer.style.alignItems = 'center';
      badgeContainer.style.gap = '2px';
      
      if (cell.visited) {
        const span = document.createElement('span');
        span.className = 'kb-text-visited';
        span.textContent = 'V';
        badgeContainer.appendChild(span);
      } else if (cell.safe) {
        const span = document.createElement('span');
        span.className = 'kb-text-safe';
        span.textContent = 'S';
        badgeContainer.appendChild(span);
      }
      
      if (cell.definitePit) {
        const span = document.createElement('span');
        span.className = 'kb-pill-pit-definite';
        span.textContent = '!';
        badgeContainer.appendChild(span);
      } else if (cell.possiblePit) {
        const span = document.createElement('span');
        span.className = 'kb-pill-pit-candidate';
        span.textContent = 'P';
        badgeContainer.appendChild(span);
      }
      
      if (cell.definiteWumpus) {
        const span = document.createElement('span');
        span.className = 'kb-pill-wumpus-definite';
        span.textContent = '@';
        badgeContainer.appendChild(span);
      } else if (cell.possibleWumpus) {
        const span = document.createElement('span');
        span.className = 'kb-pill-wumpus-candidate';
        span.textContent = 'W';
        badgeContainer.appendChild(span);
      }
      
      tile.appendChild(badgeContainer);
      DOM.kbMap.appendChild(tile);
    }
  }
}

// 7. Update Header Status Badges
function updateHeaderBadges() {
  if (!state.board) return;
  DOM.statUserId.textContent = state.userId;
  DOM.statArrows.textContent = state.board.arrowCount;
  DOM.statWumpusStatus.innerHTML = state.board.wumpusAlive ? 'ALIVE 🟢' : 'DEAD 💀';
  
  const statusColors = {
    'PLAYING': '#0ea5e9', // sky blue
    'WIN': '#eab308',     // gold yellow
    'ESCAPED': '#10b981', // emerald green
    'LOSE_PIT': '#ef4444',
    'LOSE_WUMPUS': '#ef4444'
  };
  
  DOM.statGameStatus.textContent = state.board.status;
  DOM.statGameStatus.style.color = statusColors[state.board.status] || 'inherit';
}

// 8. Process Live Action Log Message
function processActionLog(actionResult) {
  const parts = actionResult.message.split(' | ');
  const coreMessage = parts[0];
  const percept = actionResult.percept;
  
  // Format senses (Percepts)
  const senses = [];
  if (percept.breeze) senses.push('Breeze💨');
  if (percept.stench) senses.push('Stench🤢');
  if (percept.glitter) senses.push('Glitter✨');
  if (percept.bump) senses.push('Bump🧱');
  if (percept.scream) senses.push('Scream😱');
  const perceptText = senses.length > 0 ? `[지각: ${senses.join(', ')}]` : '[지각: 고요함]';
  
  // Determine prefix type
  if (coreMessage.includes('[EVENT]')) {
    addLog('이벤트 발생', `${coreMessage} ${perceptText}`, 'event-warn');
  } else if (coreMessage.includes('[Auto]')) {
    const autoMatch = coreMessage.match(/\[Auto\] 결정된 액션: (\w+)/);
    const actionName = autoMatch ? autoMatch[1] : '이동';
    addLog('🤖 Auto 결정', `${actionName} ➡️ ${perceptText}`, 'auto');
    addLog('결과 피드백', coreMessage.replace(/\[Auto\] 결정된 액션: \w+ \| /, ''), 'feedback');
  } else {
    addLog('행동 수행', `${coreMessage} ${perceptText}`, 'action');
  }
  
  // Render PathFinder recommendation
  const pathfinderPart = parts.find(p => p.startsWith('[PathFinder]'));
  if (pathfinderPart) {
    addLog('경로 탐색', pathfinderPart.replace('[PathFinder] ', ''), 'pathfinder');
  }
  
  // Special scream defeat log
  if (actionResult.message.includes('[SCREAM]')) {
    addLog('몬스터 퇴치', '움퍼스가 화살에 맞아 사망하였습니다! 👾 ➡️ 💀', 'kb-update');
  }
}

// 9. Sync State & Refresh UI
async function refreshState() {
  try {
    const statusRes = await api.get(`/status?userId=${encodeURIComponent(state.userId)}`);
    state.board = statusRes.data;
    
    const kbRes = await api.get(`/reasoning/summary?userId=${encodeURIComponent(state.userId)}`);
    state.kbSummary = kbRes.data;
    
    updateHeaderBadges();
    drawGameMap();
    drawKbMap();
  } catch (error) {
    console.error('State refresh error:', error);
    addLog('ERROR', '상태 갱신 도중 오류가 발생했습니다.', 'event-warn');
  }
}

// 10. Start Game Trigger
async function startGame() {
  const username = DOM.usernameInput.value.trim();
  if (!username) {
    alert('에이전트 이름(UserId)을 입력해주세요!');
    return;
  }
  
  state.userId = username;
  DOM.btnStart.disabled = true;
  DOM.btnStart.textContent = '생성 중...';
  
  try {
    const response = await api.post(`/start?userId=${encodeURIComponent(username)}`);
    state.board = response.data;
    state.isPlaying = true;
    
    // UI layout updates
    DOM.headerStats.style.display = 'flex';
    DOM.simulationControls.style.opacity = '1';
    DOM.simulationControls.style.pointerEvents = 'auto';
    DOM.setupGroup.style.display = 'none';
    
    addLog('SYSTEM', `새로운 Wumpus World 게임이 성공적으로 생성되었습니다! (유저: ${username})`, 'system');
    
    // Fetch initial KB reasoning
    const kbRes = await api.get(`/reasoning/summary?userId=${encodeURIComponent(username)}`);
    state.kbSummary = kbRes.data;
    addLog('KB 업데이트', '(1,1) 시작 칸을 Safe 상태로 설정하여 전진 추론을 시작합니다.', 'kb-update');
    
    // Draw
    updateHeaderBadges();
    drawGameMap();
    drawKbMap();
    
  } catch (error) {
    console.error('Game start error:', error);
    addLog('ERROR', '게임 시작 실패: 백엔드 서버 연결을 확인해주세요.', 'event-warn');
    alert('게임 시작 실패! 서버 연결 상태나 로그를 확인해주세요.');
    resetGameUI();
  } finally {
    DOM.btnStart.disabled = false;
    DOM.btnStart.textContent = '게임 시작 🚀';
  }
}

// 11. Execute Single Game Action (Manual)
async function executeAction(actionType) {
  if (!state.isPlaying) return;
  stopAutoPlay();
  
  // Cache the direction before the shot takes place
  const dirBeforeShoot = state.board ? state.board.agentDirection : 'EAST';
  
  try {
    const actionRes = await api.post(`/action?userId=${encodeURIComponent(state.userId)}&type=${actionType}`);
    processActionLog(actionRes.data);
    
    await refreshState();
    
    // Shoot Modal Alert Trigger
    if (actionType === 'SHOOT') {
      const isHit = actionRes.data.message.includes('[SCREAM]');
      const detailsText = isHit
        ? `🤠 에이전트가 ${dirBeforeShoot} 방향으로 화살을 발사하여 웜파스 괴물의 심장을 명중시켰습니다! 👾 ➡️ 💀`
        : `🤠 에이전트가 ${dirBeforeShoot} 방향으로 화살을 발사했으나, 아무 일도 일어나지 않았습니다. 🏹💨`;
      showEventModal(isHit ? "사격 명중! 🎯" : "화살 발사! 🏹", detailsText, isHit ? "🎯" : "🏹");
    }
    
    if (actionRes.data.isGameOver || state.board.status === 'ESCAPED') {
      handleGameOver();
    }
  } catch (error) {
    console.error('Action error:', error);
    addLog('ERROR', '액션 전송 중 통신 에러가 발생했습니다.', 'event-warn');
  }
}

// 12. Single Auto Action Step (1턴 자동 실행)
async function executeAutoStep() {
  if (!state.isPlaying) return;
  
  const dirBeforeShoot = state.board ? state.board.agentDirection : 'EAST';
  
  try {
    const autoRes = await api.post(`/auto?userId=${encodeURIComponent(state.userId)}`);
    processActionLog(autoRes.data);
    
    await refreshState();
    
    // Shoot Modal Alert Trigger for Auto Play
    if (autoRes.data.message.includes('결정된 액션: SHOOT')) {
      const isHit = autoRes.data.message.includes('[SCREAM]');
      const detailsText = isHit
        ? `🤖 에이전트가 자동 판단에 의해 ${dirBeforeShoot} 방향으로 화살을 발사하여 웜파스 괴물을 소탕했습니다! 👾 ➡️ 💀`
        : `🤖 에이전트가 자동 판단에 의해 ${dirBeforeShoot} 방향으로 화살을 발사했으나, 빗나갔습니다. 🏹💨`;
      showEventModal(isHit ? "자동 사격 명중! 🤖🎯" : "자동 화살 발사! 🤖🏹", detailsText, isHit ? "🎯" : "🏹");
    }
    
    if (autoRes.data.isGameOver || state.board.status === 'ESCAPED') {
      stopAutoPlay();
      handleGameOver();
    }
  } catch (error) {
    console.error('Auto step error:', error);
    addLog('ERROR', '자동 행동 수행 중 에러가 발생했습니다.', 'event-warn');
    stopAutoPlay();
  }
}

// 13. Auto Play Loop Management (에이전트 자동 실행 - 천천히 관전)
function toggleAutoPlay() {
  if (!state.isPlaying) return;
  
  if (state.isAutoPlaying) {
    stopAutoPlay();
  } else {
    startAutoPlay();
  }
}

function startAutoPlay() {
  state.isAutoPlaying = true;
  DOM.btnAutoPlay.textContent = '자동 실행 일시정지 ⏸️';
  DOM.btnAutoPlay.className = 'btn btn-danger w-100';
  
  addLog('SYSTEM', '⚡ 실시간 자동 관전 루프를 시작합니다. (동작 주기: 1.2초)', 'kb-update');
  
  // Run first action immediately
  executeAutoStep();
  
  // Set up slow interval (1.2s)
  state.autoPlayIntervalId = setInterval(() => {
    if (state.board && (state.board.status === 'PLAYING' || state.board.status === 'WIN')) {
      executeAutoStep();
    } else {
      stopAutoPlay();
    }
  }, 1200);
}

function stopAutoPlay() {
  if (!state.isAutoPlaying) return;
  state.isAutoPlaying = false;
  if (state.autoPlayIntervalId) {
    clearInterval(state.autoPlayIntervalId);
    state.autoPlayIntervalId = null;
  }
  DOM.btnAutoPlay.textContent = '에이전트 자동 실행 🤖▶️';
  DOM.btnAutoPlay.className = 'btn btn-accent-outline w-100';
  addLog('SYSTEM', '⚡ 자동 관전 루프가 일시 정지되거나 종료되었습니다.', 'system');
}

// 14. Game Over Notification Handler
function handleGameOver() {
  const status = state.board.status;
  if (status === 'ESCAPED') {
    alert('🎉 승리! 에이전트가 금을 획득하고 동굴을 무사히 탈출했습니다!');
    addLog('GAME OVER', '✨ 에이전트 탈출 성공! 승리 축하합니다! ✨', 'pathfinder');
  } else if (status === 'LOSE_PIT') {
    alert('💀 패배! 구덩이에 빠진 채 탈출하지 못하고 게임 오버되었습니다.');
    addLog('GAME OVER', '💥 에이전트 사망 (구덩이) - 패배 💥', 'event-warn');
  } else if (status === 'LOSE_WUMPUS') {
    alert('💀 패배! 웜파스에게 잡아먹혀 결국 게임 오버되었습니다.');
    addLog('GAME OVER', '💥 에이전트 사망 (잡아먹힘) - 패배 💥', 'event-warn');
  }
}

// 15. Reset Game
function resetGameUI() {
  stopAutoPlay();
  state.userId = '';
  state.isPlaying = false;
  state.board = null;
  state.kbSummary = null;
  
  // UI restoration
  DOM.headerStats.style.display = 'none';
  DOM.simulationControls.style.opacity = '0.5';
  DOM.simulationControls.style.pointerEvents = 'none';
  DOM.setupGroup.style.display = 'flex';
  DOM.usernameInput.value = 'WumpusSlayer';
  DOM.gameMap.innerHTML = '';
  DOM.kbMap.innerHTML = '';
  
  DOM.terminalLogs.innerHTML = '';
  addLog('SYSTEM', '에이전트 이름을 설정하고 게임 시작 단추를 눌러주십시오...', 'system');
}

// 16. Event Listeners Registration
DOM.btnStart.addEventListener('click', startGame);
DOM.btnReset.addEventListener('click', resetGameUI);
DOM.btnAutoStep.addEventListener('click', executeAutoStep);
DOM.btnAutoPlay.addEventListener('click', toggleAutoPlay);

DOM.btnClearLogs.addEventListener('click', () => {
  DOM.terminalLogs.innerHTML = '';
  addLog('SYSTEM', '로그 콘솔이 비워졌습니다.', 'system');
});

// Manual keys registration
DOM.btnForward.addEventListener('click', () => executeAction('GO_FORWARD'));
DOM.btnLeft.addEventListener('click', () => executeAction('TURN_LEFT'));
DOM.btnRight.addEventListener('click', () => executeAction('TURN_RIGHT'));
DOM.btnGrab.addEventListener('click', () => executeAction('GRAB'));
DOM.btnShoot.addEventListener('click', () => executeAction('SHOOT'));
DOM.btnClimb.addEventListener('click', () => executeAction('CLIMB'));

// 17. Custom Event Notification Modal Utilities
function showEventModal(title, text, icon = '🏹') {
  const modal = document.getElementById('event-modal');
  const modalTitle = document.getElementById('event-modal-title');
  const modalText = document.getElementById('event-modal-text');
  const modalIcon = modal.querySelector('.modal-icon');
  
  modalTitle.textContent = title;
  modalText.textContent = text;
  modalIcon.textContent = icon;
  
  modal.style.display = 'flex';
}

document.getElementById('btn-modal-close').addEventListener('click', () => {
  document.getElementById('event-modal').style.display = 'none';
});
