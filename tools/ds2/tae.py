"""Parser for DS2 SotFS 64-bit TAE (c000100_pl.tae). Layout checked structurally against the file:
header +0x54 u32 anim count, +0x58 u64 anim table; anim table entries (u64 id, u64 offset);
anim record 0x30: u64 events, u64 groups, u64 times, u64 animFile, i32 eventCount, i32 groupCount, i32 timeCount;
event header 0x18: u64 startTimeOffset, u64 endTimeOffset, u64 dataOffset; times are f32 seconds;
event data: i32 type, i32 pad, u64 paramsOffset."""
import struct, sys, collections

def load(path):
    d=open(path,'rb').read()
    assert d[:4]==b'TAE '
    n=struct.unpack_from('<I',d,0x54)[0]
    table=struct.unpack_from('<Q',d,0x58)[0]
    anims={}
    for i in range(n):
        aid,off=struct.unpack_from('<QQ',d,table+i*16)
        ev,gr,tm,af,ec,gc,tc=struct.unpack_from('<QQQQiii',d,off)
        events=[]
        for k in range(ec):
            s,e,dat=struct.unpack_from('<QQQ',d,ev+k*0x18)
            t0=struct.unpack_from('<f',d,s)[0]; t1=struct.unpack_from('<f',d,e)[0]
            typ,pad,po=struct.unpack_from('<iiQ',d,dat)
            events.append((typ,t0,t1,po))
        anims[aid]=events
    return d,anims

if __name__=='__main__':
    d,anims=load(sys.argv[1])
    c=collections.Counter(e[0] for ev in anims.values() for e in ev)
    print(len(anims),'anims', sum(len(v) for v in anims.values()),'events')
    print(sorted(c.items()))
