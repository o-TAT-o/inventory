// ペットをパネル内で動かすスクリプト。
//   - 何もしなければ、歩く・立ち止まる・演技を気ままに繰り返す
//   - パネルをクリックすると、その位置まで歩いてくる
//   - ペットをクリックすると反応する（click 用のアニメーションがあればそれ、無ければジャンプ）
//   - ごくまれにシークレット演出が出る。ペットを素早く5回クリックしても出せる
//   - 拡張機能から届くエディタ上の出来事（保存・エラーなど）に反応する
(function () {
  'use strict';

  const stage = document.getElementById('stage');
  const pet = document.getElementById('pet');
  const sprite = document.getElementById('sprite');
  if (!stage || !pet || !sprite) {
    return; // ペットを読み込めなかった場合は何もしない
  }

  /**
   * @typedef {{url: string, cols: number, rows: number, cells: [number, number][], delays: number[],
   *   kind: string, repeat: number, travel: boolean, speed: number, chance: number, on: string[], onChance: number}} Animation
   * @type {{frameWidth: number, frameHeight: number, displayWidth: number, sideMarginRatio: number,
   *   pixelated: boolean, float: boolean, animations: Record<string, Animation>}}
   */
  const data = JSON.parse(stage.dataset.pet || '{}');
  const animations = data.animations;
  const namesOf = (kind) => Object.keys(animations).filter((name) => animations[name].kind === kind);
  const IDLES = namesOf('idle');
  const WALKS = namesOf('walk');
  const WALKS_LEFT = namesOf('walk-left'); // あれば左右反転せず、左向きの絵を使う
  const ACTIONS = namesOf('action');
  const SECRETS = namesOf('secret');

  const SPEED = 45; // 歩く速さ（px/秒）
  const IDLE_MIN_MS = 1500;
  const IDLE_MAX_MS = 4500;
  const ACTION_CHANCE = 0.45; // 待機明けに、歩く代わりに演技をする確率
  const SECRET_CLICKS = 5; // この回数だけ素早くクリックするとシークレット演出が出る
  const SECRET_CLICK_WINDOW_MS = 2000;
  const EVENT_COOLDOWN_MS = 6000; // 同じ出来事に続けて反応しない時間
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  const random = (min, max) => min + Math.random() * (max - min);
  const pick = (list) => list[Math.floor(Math.random() * list.length)];

  // --- 見た目の初期設定 ------------------------------------------------------

  pet.style.width = `min(${data.displayWidth}px, 70vw)`;
  pet.classList.toggle('pixelated', data.pixelated);
  pet.classList.toggle('float', data.float);
  sprite.style.aspectRatio = `${data.frameWidth} / ${data.frameHeight}`;
  const shadow = pet.querySelector('.shadow');
  if (shadow) {
    shadow.style.width = `${Math.round((1 - 2 * data.sideMarginRatio) * 62)}%`;
  }

  // スプライトの左右には小道具用の余白があることがある。
  // 左側は余白ぶんだけ画面外にはみ出してよいが、右側は小道具が出るので収める。
  const minX = () => -pet.offsetWidth * data.sideMarginRatio;
  const maxX = () => Math.max(minX(), stage.clientWidth - pet.offsetWidth);

  // 画像を先読みして、切り替え時のちらつきを防ぐ
  for (const url of new Set(Object.values(animations).map((animation) => animation.url))) {
    new Image().src = url;
  }

  // --- スプライトシートの再生 ------------------------------------------------

  const player = { name: '', frame: 0, elapsed: 0, loopsLeft: Infinity, onDone: null };

  function showFrame() {
    const { cols, rows, cells } = animations[player.name];
    const [col, row] = cells[player.frame];
    sprite.style.backgroundPosition = `${cols > 1 ? (col / (cols - 1)) * 100 : 0}% ${rows > 1 ? (row / (rows - 1)) * 100 : 0}%`;
  }

  /**
   * アニメーションを最初のコマから再生する。
   * loops 回再生し終えると onDone を呼ぶ（Infinity ならループし続ける）。
   */
  function play(name, loops = Infinity, onDone = null) {
    const animation = animations[name];
    player.name = name;
    player.frame = 0;
    player.elapsed = 0;
    player.loopsLeft = loops;
    player.onDone = onDone;
    sprite.style.backgroundImage = `url("${animation.url}")`;
    sprite.style.backgroundSize = `${animation.cols * 100}% ${animation.rows * 100}%`;
    showFrame();
  }

  function advance(dtMs) {
    const animation = animations[player.name];
    player.elapsed += dtMs;
    while (player.elapsed >= animation.delays[player.frame]) {
      player.elapsed -= animation.delays[player.frame];
      if (player.frame + 1 < animation.cells.length) {
        player.frame += 1;
      } else if (--player.loopsLeft > 0) {
        player.frame = 0;
      } else {
        // 最後のコマで止めたまま、完了を通知する
        const done = player.onDone;
        player.onDone = null;
        player.elapsed = 0;
        player.loopsLeft = Infinity;
        if (done) {
          done();
        }
        return;
      }
    }
    showFrame();
  }

  // --- 行動の制御 ------------------------------------------------------------

  let mode = 'idle'; // 'idle' | 'walk' | 'action'
  let x = (minX() + maxX()) / 2;
  let targetX = x;
  let idleUntil = 0;
  let lastAction = '';
  let riding = ''; // 乗り物系（travel）のアニメーションで移動中なら、その名前
  let speedScale = 1;
  let nextSecret = Math.floor(Math.random() * Math.max(SECRETS.length, 1));
  let clickTimes = [];
  const lastEventAt = {};

  function setMode(next) {
    mode = next;
    pet.dataset.mode = next;
  }

  function startIdle(now, wait = random(IDLE_MIN_MS, IDLE_MAX_MS)) {
    setMode('idle');
    riding = '';
    speedScale = 1;
    idleUntil = now + wait;
    play(pick(IDLES));
  }

  /** 進む向きに合わせて、歩行の絵と左右反転を決める */
  function faceAndWalkAnimation(goingLeft) {
    if (goingLeft && WALKS_LEFT.length > 0) {
      pet.classList.remove('facing-left');
      return WALKS_LEFT[0];
    }
    // 絵は右向きなので、左へ進むときは反転させる
    pet.classList.toggle('facing-left', goingLeft);
    return WALKS[0];
  }

  function walkTo(destination) {
    targetX = Math.min(Math.max(destination, minX()), maxX());
    if (Math.abs(targetX - x) < 1) {
      return false;
    }
    const name = faceAndWalkAnimation(targetX < x);
    if (mode !== 'walk' || riding || player.name !== name) {
      setMode('walk');
      riding = '';
      speedScale = 1;
      play(name);
    }
    return true;
  }

  /** 演技・リアクション・シークレットを再生する */
  function perform(name) {
    const animation = animations[name];
    if (animation.travel) {
      // 乗り物系: その絵のまま、遠いほうの端まで移動する
      const left = minX();
      const right = maxX();
      targetX = x - left > right - x ? left : right;
      pet.classList.toggle('facing-left', targetX < x);
      setMode('walk');
      riding = name;
      speedScale = animation.speed;
      play(name);
      return;
    }
    setMode('action');
    riding = '';
    // 小道具は絵の右側に出るので、右向きに直してから始める
    pet.classList.remove('facing-left');
    play(name, animation.repeat, () => startIdle(performance.now()));
  }

  function startAction() {
    // 同じ演技が続かないように選ぶ
    const candidates = ACTIONS.length > 1 ? ACTIONS.filter((name) => name !== lastAction) : ACTIONS;
    lastAction = pick(candidates);
    perform(lastAction);
  }

  /** シークレット演出を順番に1つ再生する */
  function startSecret() {
    const name = SECRETS[nextSecret % SECRETS.length];
    nextSecret += 1;
    perform(name);
  }

  function decideNext(now) {
    // シークレットは、それぞれの出現確率で抽選する
    const secret = SECRETS.find((name) => Math.random() < animations[name].chance);
    if (secret) {
      perform(secret);
    } else if (ACTIONS.length > 0 && Math.random() < ACTION_CHANCE) {
      startAction();
    } else if (!walkTo(random(minX(), maxX()))) {
      startIdle(now);
    }
  }

  /**
   * 出来事に対応するアニメーションがあれば再生する。
   * @returns {boolean} 再生したかどうか
   */
  function react(eventName) {
    const now = performance.now();
    if (eventName !== 'click' && now - (lastEventAt[eventName] || -Infinity) < EVENT_COOLDOWN_MS) {
      return false;
    }
    const candidates = Object.keys(animations).filter((name) => {
      const animation = animations[name];
      return animation.on.includes(eventName) && Math.random() < animation.onChance;
    });
    if (candidates.length === 0) {
      return false;
    }
    lastEventAt[eventName] = now;
    pet.classList.remove('jumping');
    perform(pick(candidates));
    return true;
  }

  function tick(now) {
    // 非表示だった間の時間で一気に進まないよう、経過時間に上限を設ける
    const dtMs = Math.min(now - lastTime, 100);
    lastTime = now;

    if (mode === 'walk') {
      const distance = targetX - x;
      const step = (SPEED * speedScale * dtMs) / 1000;
      if (Math.abs(distance) <= step) {
        x = targetX;
        startIdle(now);
      } else {
        x += Math.sign(distance) * step;
      }
    } else if (mode === 'idle' && now >= idleUntil && !reducedMotion.matches) {
      decideNext(now);
    }

    advance(dtMs);
    pet.style.transform = `translateX(${x}px)`;
    requestAnimationFrame(tick);
  }
  let lastTime = performance.now();

  // パネルをクリックした位置まで歩く（演技中なら中断する）
  stage.addEventListener('click', (event) => {
    const rect = stage.getBoundingClientRect();
    if (!walkTo(event.clientX - rect.left - pet.offsetWidth / 2) && mode === 'action') {
      startIdle(performance.now());
    }
  });

  pet.addEventListener('click', (event) => {
    event.stopPropagation();
    const now = performance.now();
    // 素早い連続クリックでシークレット演出を出す
    clickTimes = clickTimes.filter((time) => now - time < SECRET_CLICK_WINDOW_MS);
    clickTimes.push(now);
    if (SECRETS.length > 0 && clickTimes.length >= SECRET_CLICKS) {
      clickTimes = [];
      pet.classList.remove('jumping');
      startSecret();
      return;
    }
    // click 用のアニメーションがあればそれを再生し、無ければジャンプする
    if (react('click')) {
      return;
    }
    if (mode === 'action') {
      startIdle(now);
    }
    pet.classList.remove('jumping');
    void pet.offsetWidth; // アニメーションを最初から再生し直す
    pet.classList.add('jumping');
  });
  pet.addEventListener('animationend', (event) => {
    if (event.animationName === 'jump') {
      pet.classList.remove('jumping');
    }
  });

  // 拡張機能からの通知（エディタ上の出来事、確認用の再生指示）
  window.addEventListener('message', (event) => {
    const message = event.data;
    if (!message || typeof message.name !== 'string') {
      return;
    }
    if (message.type === 'event') {
      if (message.name === 'back' && mode === 'action' && animations[player.name].on.includes('away')) {
        // 居眠りなど、離席中の演技をしていたら起こす
        startIdle(performance.now(), 500);
      }
      react(message.name);
    } else if (message.type === 'play' && animations[message.name]) {
      const kind = animations[message.name].kind;
      if (kind === 'idle') {
        startIdle(performance.now(), 6000);
        play(message.name);
      } else if (kind === 'walk' || kind === 'walk-left') {
        walkTo(kind === 'walk-left' || x > (minX() + maxX()) / 2 ? minX() : maxX());
      } else {
        perform(message.name);
      }
    }
  });

  // パネル幅が変わったら、はみ出さないように位置を補正する
  window.addEventListener('resize', () => {
    x = Math.min(Math.max(x, minX()), maxX());
    targetX = Math.min(Math.max(targetX, minX()), maxX());
  });

  startIdle(performance.now(), 1000);
  requestAnimationFrame(tick);
})();
