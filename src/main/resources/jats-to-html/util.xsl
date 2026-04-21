<?xml version="1.0" encoding="UTF-8"?>
<!--
 This stylesheet is adapted from https://github.com/PeerJ/jats-conversion/ and has the following license.
    
 * MIT License
 Copyright (c) 2022 PeerJ, Inc
  
 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 modify, - merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. 
-->
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- url-encode a string, if PHP functions are registered -->
    <xsl:template name="urlencode">
        <xsl:param name="value"/>

        <xsl:value-of select="$value"/>

        <!-- No java xslt support
        <xsl:choose>
            <xsl:when test="function-available('php:function')">
                <xsl:value-of select="php:function('rawurlencode', string($value))"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$value"/>
            </xsl:otherwise>
        </xsl:choose>
        -->
    </xsl:template>

    <!-- convert a date string to a different format -->
    <xsl:template name="format-date">
        <xsl:param name="value"/>
        <xsl:param name="format"/>

        <xsl:value-of select="$value"/>

        <!-- No java xslt support
        <xsl:value-of select="php:function('PeerJ\Conversion\JATS::formatDate', string($value), $format)"/>
        -->          
    </xsl:template>

    <!-- add full stop to a title if not already there -->
    <xsl:template name="title-punctuation">
        <xsl:param name="title" select="."/>
        <xsl:variable name="last-character" select="substring($title, string-length($title))"/>
        <xsl:if test="not(contains($end-punctuation, $last-character))">
            <xsl:text>.</xsl:text>
        </xsl:if>
    </xsl:template>

    <xsl:template name="comma-separator">
        <xsl:param name="separator" select="', '"/>
        <xsl:if test="position() != last()">
            <xsl:value-of select="$separator"/>
        </xsl:if>
    </xsl:template>
</xsl:stylesheet>
