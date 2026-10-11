import struct, sys, zipfile
# usage: addzip.py in.apk out.apk name=path ...   (rewrites, stored entries 4-byte aligned)
src, dst = sys.argv[1], sys.argv[2]
extra = [x.split('=',1) for x in sys.argv[3:]]
zin = zipfile.ZipFile(src)
items = [(i, zin.read(i.filename)) for i in zin.infolist()]
for name, path in extra:
    zi = zipfile.ZipInfo(name, date_time=(2020,1,1,0,0,0)); zi.compress_type = zipfile.ZIP_DEFLATED
    items.append((zi, open(path,'rb').read()))
with open(dst, 'wb') as f:
    zout = zipfile.ZipFile(f, 'w')
    for info, data in items:
        zi = zipfile.ZipInfo(info.filename, date_time=info.date_time); zi.compress_type = info.compress_type
        if zi.compress_type == zipfile.ZIP_STORED:
            hdr = 30 + len(zi.filename.encode())
            # well-formed alignment extra field (id 0xD935, like zipalign/apksigner)
            pad = (4 - (f.tell() + hdr + 6) % 4) % 4
            zi.extra = struct.pack('<HHH', 0xD935, 2 + pad, 4) + b'\0' * pad
        zout.writestr(zi, data)
    zout.close()
