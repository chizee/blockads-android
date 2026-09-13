package mitm

import (
	"bufio"
	"bytes"
	"compress/gzip"
	"fmt"
	"io"
	"net/http"
	"strings"
	"testing"
)

func TestInjectPlainHTML(t *testing.T) {
	rawHTML := "<!DOCTYPE html><html><head><title>Test</title></head><body>Hello</body></html>"
	rawResp := fmt.Sprintf("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: %d\r\n\r\n%s", len(rawHTML), rawHTML)

	resp, err := http.ReadResponse(bufio.NewReader(strings.NewReader(rawResp)), nil)
	if err != nil {
		t.Fatalf("ReadResponse failed: %v", err)
	}

	if !ShouldInjectHTML(resp.Header.Get("Content-Type")) {
		t.Fatalf("ShouldInjectHTML returned false")
	}

	wrapResponseForInjection(resp)

	var out bytes.Buffer
	if err := resp.Write(&out); err != nil {
		t.Fatalf("resp.Write failed: %v", err)
	}

	outStr := out.String()
	if !strings.Contains(outStr, "local.pwhs.app/cosmetic.css") {
		t.Errorf("Injected CSS not found in response:\n%s", outStr)
	}
}

func TestInjectGzipHTML(t *testing.T) {
	rawHTML := "<!DOCTYPE html><html><head><title>Test</title></head><body>Hello</body></html>"
	var gzBuf bytes.Buffer
	gw := gzip.NewWriter(&gzBuf)
	gw.Write([]byte(rawHTML))
	gw.Close()

	resp := &http.Response{
		StatusCode: 200,
		ProtoMajor: 1,
		ProtoMinor: 1,
		Header:     make(http.Header),
		Body:       io.NopCloser(bytes.NewReader(gzBuf.Bytes())),
	}
	resp.Header.Set("Content-Type", "text/html; charset=UTF-8")
	resp.Header.Set("Content-Encoding", "gzip")

	wrapResponseForInjection(resp)

	var out bytes.Buffer
	if err := resp.Write(&out); err != nil {
		t.Fatalf("resp.Write failed: %v", err)
	}

	outStr := out.String()
	if !strings.Contains(outStr, "local.pwhs.app/cosmetic.css") {
		t.Errorf("Injected CSS not found in response:\n%s", outStr)
	}
}
