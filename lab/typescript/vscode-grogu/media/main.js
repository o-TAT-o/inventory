// グローグーをパネル内で動かすスクリプト。
//   - 何もしなければ、歩く・立ち止まる・演技（カエルを食べる等）を気ままに繰り返す
//   - パネルをクリックすると、その位置まで歩いてくる
//   - グローグー自身をクリックするとジャンプする（演技中なら中断する）
//   - ごくまれにシークレット演出が出る。グローグーを素早く5回クリックしても出せる
(function () {
  'use strict';

  const stage = document.getElementById('stage');
  const pet = document.getElementById('pet');
  const sprite = document.getElementById('sprite');
  if (!stage || !pet || !sprite) {
    return; // スプライトが無い場合は何もしない
  }

  /** @type {Record<string, {url: string, kind: string, frames: number, delays: number[], repeat: number, travel: boolean, speed: number}>} */
  const animations = JSON.parse(stage.dataset.animations || '{}');
  const namesOf = (kind) => Object.keys(animations).filter((name) => animations[name].kind === kind);
  const IDLE = namesOf('idle')[0];
  const WALK = namesOf('walk')[0];
  const ACTIONS = namesOf('action');
  const SECRETS = namesOf('secret');

  const SPEED = 45; // 歩く速さ（px/秒）
  const IDLE_MIN_MS = 1500;
  const IDLE_MAX_MS = 4500;
  const ACTION_CHANCE = 0.45; // 待機明けに、歩く代わりに演技をする確率
  const SECRET_CHANCE = 0.04; // 待機明けに、シークレット演出が出る確率
  const SECRET_CLICKS = 5; // この回数だけ素早くクリックするとシークレット演出が出る
  const SECRET_CLICK_WINDOW_MS = 2000;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  sprite.style.aspectRatio = `${stage.dataset.frameWidth} / ${stage.dataset.frameHeight}`;

  const random = (min, max) => min + Math.random() * (max - min);
  // スプライトの左右には小道具用の余白（幅の1/8ほど）がある。
  // 左側は余白ぶんだけ画面外にはみ出してよいが、右側は小道具が出るので収める。
  const minX = () => -pet.offsetWidth / 8;
  const maxX = () => Math.max(minX(), stage.clientWidth - pet.offsetWidth);

  // 画像を先読みして、切り替え時のちらつきを防ぐ
  for (const name of Object.keys(animations)) {
    new Image().src = animations[name].url;
  }

  // --- スプライトシートの再生 ------------------------------------------------

  const player = { name: '', frame: 0, elapsed: 0, loopsLeft: Infinity, onDone: null };

  function showFrame() {
    const { frames } = animations[player.name];
    const position = frames > 1 ? (player.frame / (frames - 1)) * 100 : 0;
    sprite.style.backgroundPositionX = `${position}%`;
  }

  /**
   * アニメーションを最初のフレームから再生する。
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
    sprite.style.backgroundSize = `${animation.frames * 100}% 100%`;
    showFrame();
  }

  function advance(dtMs) {
    const animation = animations[player.name];
    player.elapsed += dtMs;
    while (player.elapsed >= animation.delays[player.frame]) {
      player.elapsed -= animation.delays[player.frame];
      if (player.frame + 1 < animation.frames) {
        player.frame += 1;
      } else if (--player.loopsLeft > 0) {
        player.frame = 0;
      } else {
        // 最後のフレームで止めたまま、完了を通知する
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
  let speedScale = 1; // 乗り物に乗っている間の速さの倍率
  let nextSecret = Math.floor(Math.random() * Math.max(SECRETS.length, 1));
  let clickTimes = [];
  let lastTime = performance.now();

  function setMode(next) {
    mode = next;
    pet.dataset.mode = next;
  }

  function startIdle(now, wait = random(IDLE_MIN_MS, IDLE_MAX_MS)) {
    setMode('idle');
    speedScale = 1;
    idleUntil = now + wait;
    play(IDLE);
  }

  function walkTo(destination) {
    targetX = Math.min(Math.max(destination, minX()), maxX());
    if (Math.abs(targetX - x) < 1) {
      return false;
    }
    // 絵は右向きなので、左へ進むときは反転させる
    pet.classList.toggle('facing-left', targetX < x);
    if (mode !== 'walk' || speedScale !== 1) {
      setMode('walk');
      speedScale = 1;
      play(WALK);
    }
    return true;
  }

  function startAction() {
    // 同じ演技が続かないように選ぶ
    const candidates = ACTIONS.length > 1 ? ACTIONS.filter((name) => name !== lastAction) : ACTIONS;
    const name = candidates[Math.floor(Math.random() * candidates.length)];
    lastAction = name;
    setMode('action');
    // 小道具は絵の右側に出るので、右向きに直してから始める
    pet.classList.remove('facing-left');
    play(name, animations[name].repeat, () => startIdle(performance.now()));
  }

  /** シークレット演出を順番に1つ再生する */
  function startSecret() {
    const name = SECRETS[nextSecret % SECRETS.length];
    nextSecret += 1;
    const animation = animations[name];
    if (!animation.travel) {
      setMode('action');
      pet.classList.remove('facing-left');
      play(name, animation.repeat, () => startIdle(performance.now()));
      return;
    }
    // 乗り物系: その絵のまま、遠いほうの端まで移動する
    const left = minX();
    const right = maxX();
    targetX = x - left > right - x ? left : right;
    pet.classList.toggle('facing-left', targetX < x);
    setMode('walk');
    speedScale = animation.speed;
    play(name);
  }

  function decideNext(now) {
    if (SECRETS.length > 0 && Math.random() < SECRET_CHANCE) {
      startSecret();
    } else if (ACTIONS.length > 0 && Math.random() < ACTION_CHANCE) {
      startAction();
    } else if (!walkTo(random(minX(), maxX()))) {
      startIdle(now);
    }
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

  // パネルをクリックした位置まで歩く（演技中なら中断する）
  stage.addEventListener('click', (event) => {
    const rect = stage.getBoundingClientRect();
    if (!walkTo(event.clientX - rect.left - pet.offsetWidth / 2) && mode === 'action') {
      startIdle(performance.now());
    }
  });

  // グローグーをクリックするとジャンプする（演技中なら中断して起こす）
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

  // パネル幅が変わったら、はみ出さないように位置を補正する
  window.addEventListener('resize', () => {
    x = Math.min(x, maxX());
    targetX = Math.min(targetX, maxX());
  });

  startIdle(performance.now(), 1000);
  requestAnimationFrame(tick);
})();
