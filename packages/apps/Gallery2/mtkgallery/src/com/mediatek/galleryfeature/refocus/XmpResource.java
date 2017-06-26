package com.mediatek.galleryfeature.refocus;

public class XmpResource {
    public static final String EXIF_HEADER = "Exif";

    ///////////////////////////XMP MAIN INFO////////////////////////////////////
    public static final String XMP_HEADER_START = "http://ns.adobe.com/xap/1.0/\0";
    public static final String XMP_HEADER =
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.1.0-jc003\">\n"
        + "  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
        + "    <rdf:Description rdf:about=\"\"\n"
        + "        xmlns:GFocus=\"http://ns.google.com/photos/1.0/focus/\"\n"
        + "        xmlns:GImage=\"http://ns.google.com/photos/1.0/image/\"\n"
        + "        xmlns:GDepth=\"http://ns.google.com/photos/1.0/depthmap/\"\n"
        + "        xmlns:xmpNote=\"http://ns.adobe.com/xmp/note/\"\n"
        + "      GFocus:BlurAtInfinity=\"0.036340434\"\n"
        + "      GFocus:FocalDistance=\"28.816347\"\n"
        + "      GFocus:FocalPointX=\"0.5\"\n"
        + "      GFocus:FocalPointY=\"0.5\"\n"
        + "      GImage:Mime=\"image/jpeg\"\n"
        + "      GDepth:Format=\"RangeInverse\"\n"
        + "      GDepth:Near=\"8.769096374511719\"\n"
        + "      GDepth:Far=\"46.351776123046875\"\n"
        + "      GDepth:Mime=\"image/png\"\n"
        + "      xmpNote:HasExtendedXMP=\"53C952F404D76BCEACA548091A61B44F\"/>\n"
        + "  </rdf:RDF>\n"
        + "</x:xmpmeta>\n";

    ///////////////////////////XMP EXTENSION MAIN(1ST EXTENSION PACKET)////////////
    public static final String XMP_EXT_MAIN_HEADER1 =
        "http://ns.adobe.com/xmp/extension/";

    public static final byte XMP_EXT_MAIN_HEADER_GAP = 0x0;

    public static final String XMP_EXT_MAIN_HEADER2 = 
        "53C952F404D76BCEACA548091A61B44F";

    // between these 2 string, need to add byte[0x00,0x05,0xb2,0xd5,0x0,0x0,0x0,0x0]
    // use [0x00,0x05,0x05,0x05,0x0,0x0,0x0,0x0] instead of [0x00,0x05,0xb2,0xd5,0x0,0x0,0x0,0x0]
    // at last we may replace back
    public static final byte[] XMP_EXT_MAIN_BYTES =
        new byte[] {0x00,0x05,/*0xb2*/0x05,/*0xd5*/0x05,0x0,0x0,0x0,0x0};

    public static final int XMP_EXT_REPLACE_2_BYTES_OFFSET = 0x45; // use this to replace 0x05,0x05

    public static final int XMP_EXT_REPLACE_2_BYTES = 0xb2d5;

    public static final String XMP_EXT_MAIN_MID =
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.1.0-jc003\">\n"
        + "  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
        + "    <rdf:Description rdf:about=\"\"\n"
        + "        xmlns:GImage=\"http://ns.google.com/photos/1.0/image/\"\n"
        + "        xmlns:GDepth=\"http://ns.google.com/photos/1.0/depthmap/\"\n"
        + "      GImage:Data=\"";

    //////////////////XMP EXTENSION SLAVE(2nd, 3rd... EXTENSION PACKET)////////////
    public static final String XMP_EXT_SLAVE_HEAD1 = 
        "http://ns.adobe.com/xmp/extension/";

    public static final byte XMP_EXT_SLAVE_HEADER_GAP = 0x0;

    public static final String XMP_EXT_SLAVE_HEAD2 = 
        "53C952F404D76BCEACA548091A61B44F";

    // need to add byte[0x00,0x05,0xb2,0xd5,0x0,0x0]
    public static final byte[] XMP_EXT_SLAVE_BYTES = new byte[] {0x00,0x05,/*0xb2*/0x05,/*0xd5*/0x05,0x0};

    ///////////////////////////XMP EXTENSION TAIL///////////////////////////////////
    public static final String XMP_EXTENSION_TAIL = "\"/>\n  </rdf:RDF>\n</x:xmpmeta>\n";

    ///////////////////////////XMP JPS AND MASK//////////////////////////////////////
    // JPS Format like this:   <x:xmpmeta MtkJps:Data="xxxxxxx"\n</x:xmpmeta>\n
    // JPS mask Format like this:   <x:xmpmeta MtkJpsMask:Data="xxxxxxx"\n</x:xmpmeta>\n
    // public static final String XMP_JPS_HEADER = "<x:xmpmeta MtkJps:Data=\"";
    // public static final String XMP_JPS_TAIL = "\"\n</x:xmpmeta>\n";
    // public static final String XMP_JPS_MASK_HEADER = "<x:xmpmeta MtkJpsMask:Data=\"";
    // public static final String XMP_JPS_MASK_TAIL = "\"\n</x:xmpmeta>\n";

    // JPS DATA format:
    // APP1 TAG (2 BYTES)
    // APP1 Length (2 BYTES)
    // JPS DATA length (4 BYTES)
    // "JPSDATA"(7 BYTES)
    // JPS SerialNumber(1 BYTE)
    // JPSDATA (whole jps data)

    // JPS MASK format:
    // APP1 TAG (2 BYTES)
    // APP1 Length (2 BYTES)
    // JPS MASK length (4 BYTES)
    // "JPSMASK"(7 BYTES)
    // JPS SerialNumber(1 BYTE)
    // JPSMASK (whole jps mask data)
    public static final String TYPE_JPS_DATA = "JPSDATA";
    public static final String TYPE_JPS_MASK = "JPSMASK";
    public static final String TYPE_DEPTH_DATA = "DEPTHBF";
    public static final String TYPE_XMP_DEPTH = "XMPDEPT";
    public static final int TOTAL_LENGTH_TAG_BYTE = 4; // use 4 bytes for JPS data/mask length
    public static final int JPS_SERIAL_NUM_TAG_BYTE = 1;// use 1 byte for package serial number
    public static final int JPS_PACKET_SIZE = 0xffb4 - 2 -TOTAL_LENGTH_TAG_BYTE; // same with XMP, but reserve 4 bytes for total length
    public static final int JPS_PURE_DATA_SIZE_PER_PACKET = JPS_PACKET_SIZE - TYPE_JPS_DATA.length()
                                              - JPS_SERIAL_NUM_TAG_BYTE;
    public static final int JPS_PACKET_HEAD_SIZE_EXCLUDE_DATA = 2 + 2 + TOTAL_LENGTH_TAG_BYTE
                                              + TYPE_JPS_DATA.length() + JPS_SERIAL_NUM_TAG_BYTE;

    public static final int DEPTH_SERIAL_NUM_TAG_BYTE = JPS_SERIAL_NUM_TAG_BYTE;
    public static final int DEPTH_PACKET_SIZE = JPS_PACKET_SIZE;
    public static final int DEPTH_PURE_DATA_SIZE_PER_PACKET = JPS_PURE_DATA_SIZE_PER_PACKET;
    public static final int DEPTH_PACKET_HEAD_SIZE_EXCLUDE_DATA = JPS_PACKET_HEAD_SIZE_EXCLUDE_DATA;
}
