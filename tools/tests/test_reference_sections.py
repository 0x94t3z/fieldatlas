import unittest

from tools.reference_sections import sections, source_record


class ReferenceSectionsTest(unittest.TestCase):
    def test_nested_sections_preserve_scope_order_and_negation(self):
        raw = '''<document xmlns="http://cnx.rice.edu/cnxml"><title>Cells</title>
        <content><para>Introduction.</para><section><title>Division</title>
        <para>One division.</para><section><title>Exception</title>
        <para>Not identical.</para></section><para>Final qualification.</para>
        </section></content></document>'''
        self.assertEqual(sections(raw), [
            (('Cells',), 'Introduction.'),
            (('Cells', 'Division'), 'One division.'),
            (('Cells', 'Division', 'Exception'), 'Not identical.'),
            (('Cells', 'Division'), 'Final qualification.')])

    def test_inline_footnotes_do_not_fuse_into_sentences(self):
        raw = '''<document><title>Book</title><content>
        <para>Author<footnote>Bibliography.</footnote> explained <emphasis>why</emphasis>.</para>
        <figure><caption>Not licensed here.</caption></figure>
        <exercise><para>Quiz.</para></exercise><para>However, not always.</para>
        </content></document>'''
        self.assertEqual(sections(raw), [(('Book',), 'Author explained why.\n\nHowever, not always.')])

    def test_original_fields_not_editorial_warning(self):
        self.assertEqual(source_record('GenAge', {'symbol': 'AR', 'why': 'upstream'}),
                         'Dataset: GenAge\nsymbol: AR\nwhy: upstream')

    def test_empty_and_invalid_fields(self):
        self.assertEqual(source_record('Data', {'a': '', 'b': 'no'}), 'Dataset: Data\nb: no')
        with self.assertRaises(ValueError):
            source_record('Data', {'a': None})
        with self.assertRaises(ValueError):
            sections('<document><content/></document>')


if __name__ == '__main__':
    unittest.main()
