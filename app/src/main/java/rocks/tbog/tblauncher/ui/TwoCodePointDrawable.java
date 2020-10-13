package rocks.tbog.tblauncher.ui;

public class TwoCodePointDrawable extends TextDrawable {

    final int mCodePoint1;
    final int mCodePoint2;
    private boolean bVertical = false;

    public TwoCodePointDrawable(int cp1, int cp2) {
        super();
        mCodePoint1 = cp1;
        mCodePoint2 = cp2;
    }

    public static TwoCodePointDrawable fromText(CharSequence text) {
        int cp1 = Character.codePointAt(text, 0);
        int cp2 = Character.codePointAt(text, Character.charCount(cp1));
        return new TwoCodePointDrawable(cp1, cp2);
    }

    public void setVertical(boolean vertical) {
        bVertical = vertical;
    }

    @Override
    protected int getLineCount() {
        return bVertical ? 2 : 1;
    }

    @Override
    protected char[] getText(int line) {
        if (bVertical)
            return line == 0 ? Character.toChars(mCodePoint1) : Character.toChars(mCodePoint2);
        int c1 = Character.charCount(mCodePoint1);
        int c2 = Character.charCount(mCodePoint2);
        char[] result = new char[c1 + c2];
        System.arraycopy(Character.toChars(mCodePoint1), 0, result, 0, c1);
        System.arraycopy(Character.toChars(mCodePoint2), 0, result, c1, c2);
        return result;
    }
}
