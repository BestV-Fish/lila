import * as keyboard from '../keyboard';
import * as promotion from '../promotion';
import * as util from '../util';
import crazyView from '../crazy/crazyView';
import RoundController from '../ctrl';
import { h, VNode } from 'snabbdom';
import { plyStep } from '../round';
import { Position, MaterialDiff, MaterialDiffSide, CheckCount } from '../interfaces';
import { read as fenRead } from 'chessground/fen';
import { render as keyboardMove } from '../keyboardMove';
import { render as renderGround } from '../ground';
import { renderTable } from './table';

function renderMaterial(
  material: MaterialDiffSide,
  score: number,
  position: Position,
  noMaterial: boolean,
  checks?: number
) {
  if (noMaterial) return;
  const children: VNode[] = [];
  let role: string, i: number;
  for (role in material) {
    if (material[role] > 0) {
      const content: VNode[] = [];
      for (i = 0; i < material[role]; i++) content.push(h('mpiece.' + role));
      children.push(h('div', content));
    }
  }
  if (checks) for (i = 0; i < checks; i++) children.push(h('div', h('mpiece.k-piece')));
  if (score > 0) children.push(h('score', '+' + score));
  return h('div.material.material-' + position, children);
}

function renderPlayerScore(score: number, position: Position, playerIndex: string, variantKey: VariantKey): VNode {
  //TODO change the g-piece to reflect the score in oware or keep orange box in css/score.scss
  const pieceClass = variantKey === 'oware' ? 'piece.g-piece.' : 'piece.p-piece.';
  const children: VNode[] = [];
  children.push(h(pieceClass + playerIndex, { attrs: { 'data-score': score } }));
  return h('div.game-score.game-score-' + position, children);
}

function wheel(ctrl: RoundController, e: WheelEvent): void {
  if (!ctrl.isPlaying()) {
    e.preventDefault();
    if (e.deltaY > 0) keyboard.next(ctrl);
    else if (e.deltaY < 0) keyboard.prev(ctrl);
    ctrl.redraw();
  }
}

const emptyMaterialDiff: MaterialDiff = {
  p1: {},
  p2: {},
};

export function main(ctrl: RoundController): VNode {
  const d = ctrl.data,
    cgState = ctrl.chessground && ctrl.chessground.state,
    topPlayerIndex = d[ctrl.flip ? 'player' : 'opponent'].playerIndex,
    bottomPlayerIndex = d[ctrl.flip ? 'opponent' : 'player'].playerIndex,
    boardSize = d.game.variant.boardSize,
    varaintKey = d.game.variant.key;
  let topScore = 0,
    bottomScore = 0;
  if (d.hasGameScore) {
    if (varaintKey === 'flipello') {
      const pieces = cgState ? cgState.pieces : fenRead(plyStep(ctrl.data, ctrl.ply).fen, boardSize);
      const p1Score = util.getPlayerScore(varaintKey, pieces, 'p1');
      const p2Score = util.getPlayerScore(varaintKey, pieces, 'p2');
      topScore = topPlayerIndex === 'p1' ? p1Score : p2Score;
      bottomScore = topPlayerIndex === 'p2' ? p1Score : p2Score;
    } else {
      //TODO update score based on oware (make general function in util?)
      const p1Score = 2;
      const p2Score = 3;
      topScore = topPlayerIndex === 'p1' ? p1Score : p2Score;
      bottomScore = topPlayerIndex === 'p2' ? p1Score : p2Score;
    }
  }

  let material: MaterialDiff,
    score = 0;
  if (d.pref.showCaptured) {
    const pieces = cgState ? cgState.pieces : fenRead(plyStep(ctrl.data, ctrl.ply).fen, boardSize);
    material = util.getMaterialDiff(pieces);
    score = util.getScore(varaintKey, pieces) * (bottomPlayerIndex === 'p1' ? 1 : -1);
  } else material = emptyMaterialDiff;

  const checks: CheckCount =
    d.player.checks || d.opponent.checks ? util.countChecks(ctrl.data.steps, ctrl.ply) : util.noChecks;

  // fix coordinates for non-chess games to display them outside due to not working well displaying on board
  if (['xiangqi', 'shogi', 'minixiangqi', 'minishogi', 'flipello', 'oware'].includes(varaintKey)) {
    if (!$('body').hasClass('coords-no')) {
      $('body').removeClass('coords-in').addClass('coords-out');
    }
  }

  //Add piece-letter class for games which dont want Noto Chess (font-famliy)
  const notationBasic = ['xiangqi', 'shogi', 'minixiangqi', 'minishogi'].includes(varaintKey) ? '.piece-letter' : '';

  return ctrl.nvui
    ? ctrl.nvui.render(ctrl)
    : h(
        `div.round__app.variant-${varaintKey}${notationBasic}.${d.game.gameFamily}`,
        {
          class: { 'move-confirm': !!(ctrl.moveToSubmit || ctrl.dropToSubmit) },
        },
        [
          h(
            'div.round__app__board.main-board' + (ctrl.data.pref.blindfold ? '.blindfold' : ''),
            {
              hook:
                'ontouchstart' in window
                  ? undefined
                  : util.bind('wheel', (e: WheelEvent) => wheel(ctrl, e), undefined, false),
            },
            [renderGround(ctrl), promotion.view(ctrl)]
          ),
          ctrl.data.hasGameScore ? renderPlayerScore(topScore, 'top', topPlayerIndex, varaintKey) : null,
          crazyView(ctrl, topPlayerIndex, 'top') ||
            renderMaterial(material[topPlayerIndex], -score, 'top', d.hasGameScore, checks[topPlayerIndex]),
          ...renderTable(ctrl),
          crazyView(ctrl, bottomPlayerIndex, 'bottom') ||
            renderMaterial(material[bottomPlayerIndex], score, 'bottom', d.hasGameScore, checks[bottomPlayerIndex]),
          ctrl.data.hasGameScore ? renderPlayerScore(bottomScore, 'bottom', bottomPlayerIndex, varaintKey) : null,
          ctrl.keyboardMove ? keyboardMove(ctrl.keyboardMove) : null,
        ]
      );
}
