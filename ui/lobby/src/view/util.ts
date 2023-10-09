import { h, Hooks } from 'snabbdom';
import { MaybeVNodes } from '../interfaces';

export function bind(eventName: string, f: (e: Event) => any, redraw?: () => void): Hooks {
  return {
    insert(vnode) {
      (vnode.elm as HTMLElement).addEventListener(eventName, e => {
        const res = f(e);
        if (redraw) redraw();
        return res;
      });
    },
  };
}

export function tds(bits: MaybeVNodes): MaybeVNodes {
  return bits.map(function (bit) {
    return h('td', [bit]);
  });
}

export function spinner() {
  return h('div.spinner', [
    h('svg', { attrs: { viewBox: '0 0 40 40' } }, [
      h('circle', {
        attrs: { cx: 20, cy: 20, r: 18, fill: 'none' },
      }),
    ]),
  ]);
}

export const perfIcons = {
  Blitz: ')',
  'Racing Kings': '',
  UltraBullet: '{',
  Bullet: 'T',
  Classical: '+',
  Rapid: '#',
  'Three-check': '',
  'Five-check': '',
  Antichess: '@',
  Horde: '_',
  Atomic: '>',
  Crazyhouse: '',
  Chess960: "'",
  Correspondence: ';',
  'King of the Hill': '(',
  'No Castling': '',
  'Lines Of Action': '',
  'Scrambled Eggs': '',
  International: '',
  Frisian: '',
  Frysk: '',
  Antidraughts: '',
  Breakthrough: '',
  Russian: '',
  Brazilian: '',
  Pool: '',
  Portuguese: '',
  English: '‹',
  Shogi: '',
  Xiangqi: '',
  'Mini Shogi': '',
  'Mini Xiangqi': '',
  Flipello: '',
  Flipello10: '',
  Amazons: '€',
  Oware: '',
  Togyzkumalak: '›',
  Go9x9: '',
  Go13x13: '',
  Go19x19: '',
};
