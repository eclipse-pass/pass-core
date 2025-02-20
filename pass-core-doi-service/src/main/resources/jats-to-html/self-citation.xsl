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
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:str="http://exslt.org/strings"
                extension-element-prefixes="str">

    <!-- self citation -->
    <xsl:template name="self-citation">
        <xsl:param name="meta"/>

        <dl>
            <dt>Cite this article</dt>
            <dd>
                <xsl:call-template name="self-citation-authors"/>
                <xsl:text>&#32;</xsl:text>
                <span>
                    <xsl:value-of select="$pub-date/year"/>
                </span>
                <xsl:text>.&#32;</xsl:text>
                <xsl:apply-templates select="$title" mode="self-citation"/>
                <xsl:call-template name="title-punctuation">
                    <xsl:with-param name="title" select="$title"/>
                </xsl:call-template>
                <xsl:text>&#32;</xsl:text>
	            <span itemprop="isPartOf" itemscope="itemscope"
	                  itemtype="http://schema.org/PublicationVolume">
		            <span
		                  itemprop="isPartOf" itemscope="itemscope"
		                  itemtype="http://schema.org/Periodical">
			            <span itemprop="name">
				            <xsl:value-of select="$journal-title"/>
			            </span>
		            </span>
		            <xsl:text>&#32;</xsl:text>
		            <span itemprop="volumeNumber">
			            <xsl:value-of select="$meta/volume"/>
		            </span>
	            </span>
                <xsl:text>:</xsl:text>
                <span itemprop="pageStart">
                    <xsl:value-of select="$meta/elocation-id"/>
                </span>
                <xsl:text>&#32;</xsl:text>
                <a href="https://doi.org/{$doi}" itemprop="url">
                    <xsl:value-of select="concat('https://doi.org/', $doi)"/>
                </a>
            </dd>
        </dl>
    </xsl:template>

    <!-- self citation author names -->
    <xsl:template name="self-citation-authors">
        <span>
            <xsl:apply-templates select="$authors/name | $authors/collab" mode="self-citation"/>
            <xsl:text>.</xsl:text>
        </span>
    </xsl:template>

    <xsl:template match="article-title" mode="self-citation">
        <span>
            <xsl:apply-templates select="node()|@*"/>
        </span>
    </xsl:template>

       <xsl:template match="name" mode="self-citation">
        <xsl:apply-templates select="surname" mode="self-citation"/>
        <xsl:if test="given-names">
            <xsl:text>&#32;</xsl:text>
            <xsl:apply-templates select="given-names" mode="self-citation"/>
        </xsl:if>
        <xsl:if test="suffix">
            <xsl:text>&#32;</xsl:text>
            <xsl:apply-templates select="suffix" mode="self-citation"/>
        </xsl:if>
        <xsl:call-template name="comma-separator"/>
    </xsl:template>

    <xsl:template match="surname" mode="self-citation">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="suffix" mode="self-citation">
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="given-names" mode="self-citation">
        <xsl:choose>
            <xsl:when test="@initials">
                <xsl:value-of select="@initials"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:for-each select="str:tokenize(., ' .')">
                    <xsl:value-of select="substring(., 1, 1)"/>
                </xsl:for-each>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="collab" mode="self-citation">
        <xsl:apply-templates/>
        <xsl:call-template name="comma-separator"/>
    </xsl:template>
</xsl:stylesheet>
