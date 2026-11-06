"""DS2 SotFS ESD ("fsSL", EzState v2.25, formato largo) -> texto. Uso: esd.py <in.esd> <out.txt>. Estados: id, condiciones, entry, exit, while (orden del archivo). Ver docs/research/enemy_ai.md."""
import struct,sys
D=0x6C
OPS={0x8C:'+',0x8D:'neg',0x8E:'-',0x8F:'*',0x90:'/',0x91:'<=',0x92:'>=',0x93:'<',0x94:'>',0x95:'==',0x96:'!=',0x98:'&&',0x99:'||',0x9A:'!'}
def expr(b):
    st=[];i=0;out=[]
    while i<len(b):
        o=b[i];i+=1
        if o<0x80: st.append(str(o-64))
        elif o==0x80: st.append(repr(round(struct.unpack_from('<f',b,i)[0],4)));i+=4
        elif o==0x81: st.append(repr(struct.unpack_from('<d',b,i)[0]));i+=8
        elif o==0x82: st.append(str(struct.unpack_from('<i',b,i)[0]));i+=4
        elif 0x84<=o<=0x8A:
            n=o-0x84; args=st[len(st)-n:] if n else []; 
            if n: del st[-n:]
            f=st.pop(); st.append('f%s(%s)'%(f,','.join(args)))
        elif o in OPS:
            if o in (0x8D,0x9A): a=st.pop(); st.append('%s(%s)'%(OPS[o],a))
            else: b2=st.pop();a=st.pop(); st.append('(%s %s %s)'%(a,OPS[o],b2))
        elif o==0xA1: break
        elif o==0xA5:
            j=i
            while b[j:j+2]!=b'\0\0': j+=2
            st.append('"%s"'%b[i:j].decode('utf-16le')); i=j+2
        elif o==0xA6: st.append('ABORT_IF_FALSE(%s)'%st.pop())
        elif 0xA7<=o<=0xAE: st.append('r%d=%s'%(o-0xA7,st.pop()))
        elif 0xAF<=o<=0xB6: st.append('r%d'%(o-0xAF))
        elif o==0xB7: st.append('B7(%s)'%st.pop())
        elif o==0xB8: st.append('env(%s)'%st.pop())
        elif o==0xB9: st.append('B9')
        elif o==0xBA: st.append('BA')
        else: st.append('?%02x'%o)
    return ' ; '.join(st)
class ESD:
    def __init__(s,path):
        d=s.d=open(path,'rb').read()
        assert d[:4]==b'fsSL'
        s.L=lambda o: struct.unpack_from('<q',d,D+o)[0]
        sgo=s.L(0x18); sgn=s.L(0x20); no=s.L(0x28); nl=s.L(0x30)
        s.name=d[D+no:D+no+nl*2].decode('utf-16le','replace')
        s.groups={}
        for g in range(sgn):
            gid,so,sc,_=struct.unpack_from('<qqqq',d,D+sgo+g*0x20)
            states={}
            for k in range(sc):
                v=struct.unpack_from('<9q',d,D+so+k*0x48)
                states[v[0]]=dict(entry=s.cmds(v[3],v[4]),exit=s.cmds(v[5],v[6]),whil=s.cmds(v[7],v[8]),conds=[s.cond(struct.unpack_from('<q',d,D+v[1]+c*8)[0]) for c in range(v[2])],off=so+k*0x48)
            s.groups[gid]=states
    def cmds(s,o,n):
        r=[]
        for k in range(n):
            bank,cid,ao,ac=struct.unpack_from('<iiqq',s.d,D+o+k*0x18)
            args=[]
            for a in range(ac):
                eo,el=struct.unpack_from('<qq',s.d,D+ao+a*0x10)
                args.append(expr(s.d[D+eo:D+eo+el]))
            r.append((bank,cid,args))
        return r
    def cond(s,o):
        v=struct.unpack_from('<7q',s.d,D+o)
        return dict(next=v[0],pass_=s.cmds(v[1],v[2]),sub=[s.cond(struct.unpack_from('<q',s.d,D+v[3]+c*8)[0]) for c in range(v[4])],ev=expr(s.d[D+v[5]:D+v[5]+v[6]]))
    def stid(s,off):
        for g,st in s.groups.items():
            for k,v in st.items():
                if v['off']==off: return '%d:%d'%(g,k)
        return 'END' if off==-1 else '@%x'%off
def dump(e,out):
    def pc(c,ind):
        out.write('%s  if %s -> %s\n'%(ind,c['ev'],e.stid(c['next'])))
        for b,i,a in c['pass_']: out.write('%s     pass cmd %d:%d(%s)\n'%(ind,b,i,', '.join(a)))
        for sc in c['sub']: pc(sc,ind+'    ')
    out.write('NAME %s\n'%e.name)
    for g in sorted(e.groups):
        out.write('\n== group %d (%d states)\n'%(g,len(e.groups[g])))
        for k in sorted(e.groups[g]):
            v=e.groups[g][k]; out.write(' state %d\n'%k)
            for nm in ('entry','whil','exit'):
                for b,i,a in v[nm]: out.write('   %s cmd %d:%d(%s)\n'%(nm,b,i,', '.join(a)))
            for c in v['conds']: pc(c,'  ')
if __name__=='__main__':
    e=ESD(sys.argv[1]); dump(e,open(sys.argv[2],'w',encoding='utf-8'))
